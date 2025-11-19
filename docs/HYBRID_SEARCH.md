# Hybrid Search Implementation

## Overview

This implementation adds hybrid search capabilities to the Intelligent Support Assistant, combining vector similarity search with keyword-based search (BM25) and cross-encoder reranking for improved retrieval accuracy.

## Architecture

The hybrid search pipeline consists of three main stages:

1. **Parallel Retrieval**: Vector search (Weaviate) + Keyword search (Elasticsearch)
2. **Rank Fusion**: Reciprocal Rank Fusion (RRF) algorithm
3. **Reranking**: Cross-encoder scoring for final ranking

```
Query
  ├─> Vector Search (Weaviate) ──┐
  │                               ├─> RRF Fusion ─> Reranking ─> Top K Results
  └─> Keyword Search (ES/BM25) ──┘
```

## Components

### 1. Elasticsearch Integration

**SearchableDocument**: Elasticsearch document model with text fields optimized for BM25 search.

**KeywordSearchService**: Performs BM25-based full-text search using Elasticsearch.

**Configuration**:
```yaml
elasticsearch:
  host: localhost:9200
  username: ""
  password: ""
```

### 2. Hybrid Search Service

**HybridSearchService**: Orchestrates the hybrid search pipeline.

**RRF Algorithm**:
```
score(d) = vector_weight / (k + rank_vector(d)) + keyword_weight / (k + rank_keyword(d))
```

**Configuration**:
```yaml
hybrid-search:
  vector-weight: 0.6      # Weight for vector search results
  keyword-weight: 0.4     # Weight for keyword search results
  rrf-k: 60              # RRF constant (typically 60)
  use-reranking: true    # Enable cross-encoder reranking
```

### 3. Reranker Service

**RerankerService**: Scores query-document pairs using cross-encoder approach.

Current implementation uses semantic similarity as a baseline. Can be extended with:
- External reranking APIs (Cohere Rerank, Jina AI)
- Local ONNX models
- Python microservice with transformers library

**Configuration**:
```yaml
reranker:
  enabled: true
  top-k: 10
```

## Usage

### Enable/Disable Hybrid Search

```yaml
query:
  use-hybrid-search: true  # Set to false to use vector search only
```

### API Request

```json
POST /api/v1/query
{
  "query": "How do I reset my password?",
  "sessionId": "session-123",
  "context": {
    "documentType": "faq"
  }
}
```

### Response Metadata

The response includes metadata about the retrieval method:

```json
{
  "response": "...",
  "sources": [...],
  "confidenceScore": 1.0,
  "metadata": {
    "tokensUsed": 150,
    "latencyMs": 450,
    "modelUsed": "gpt-4-turbo-preview",
    "timestamp": "2024-01-15T10:30:00Z",
    "additionalInfo": {
      "documentsRetrieved": 10,
      "retrievalMethod": "hybrid_search"
    }
  }
}
```

## Document Ingestion

Documents are automatically indexed in both Weaviate (for vector search) and Elasticsearch (for keyword search) during ingestion:

```java
POST /api/v1/documents
{
  "content": "...",
  "filename": "user-guide.pdf",
  "documentType": "pdf",
  "metadata": {...}
}
```

## Performance Considerations

1. **Retrieval Size**: The system retrieves 2x the requested top-k results (20 documents) for better fusion and reranking.

2. **Caching**: Query embeddings are cached to reduce API calls.

3. **Fallback**: If hybrid search fails, the system falls back to vector search only.

4. **Parallel Execution**: Vector and keyword searches can be executed in parallel for better performance.

## Learning Resources

### BM25 Algorithm
- Full-text search algorithm based on probabilistic information retrieval
- Uses term frequency (TF) and inverse document frequency (IDF)
- Better than TF-IDF for handling document length normalization

### Reciprocal Rank Fusion (RRF)
- Simple yet effective rank fusion algorithm
- Combines multiple ranked lists without requiring score normalization
- Formula: `score(d) = Σ 1/(k + rank(d))` where k is typically 60

### Cross-Encoders vs Bi-Encoders
- **Bi-encoders**: Encode query and document separately (used in vector search)
  - Fast: Can pre-compute document embeddings
  - Less accurate: No query-document interaction
  
- **Cross-encoders**: Encode query-document pair together (used in reranking)
  - Slow: Must compute for each query-document pair
  - More accurate: Captures query-document interaction

## Future Enhancements

1. **Advanced Reranking**: Integrate with external reranking APIs or local models
2. **Query Expansion**: Expand queries with synonyms before keyword search
3. **Semantic Chunking**: Use semantic boundaries for better chunk quality
4. **Hybrid Filtering**: Apply metadata filters to both vector and keyword search
5. **A/B Testing**: Compare hybrid search vs pure vector search performance

## Troubleshooting

### Elasticsearch Connection Issues
- Verify Elasticsearch is running: `curl http://localhost:9200`
- Check credentials in application.yml
- Review logs for connection errors

### Poor Keyword Search Results
- Verify documents are indexed: Check Elasticsearch index
- Adjust BM25 parameters if needed
- Consider adding custom analyzers for domain-specific terms

### Reranking Performance
- Monitor reranking latency in logs
- Consider disabling reranking for simple queries
- Implement caching for frequently reranked results
