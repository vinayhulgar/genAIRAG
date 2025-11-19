package com.support.assistant.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for hybrid search combining vector similarity and keyword search.
 * Uses Reciprocal Rank Fusion (RRF) algorithm to merge ranked lists.
 * Supports reactive async execution with Mono.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HybridSearchService {

    private final RetrievalService retrievalService;
    private final KeywordSearchService keywordSearchService;
    private final RerankerService rerankerService;

    @Value("${hybrid-search.vector-weight:0.6}")
    private double vectorWeight;

    @Value("${hybrid-search.keyword-weight:0.4}")
    private double keywordWeight;

    @Value("${hybrid-search.rrf-k:60}")
    private int rrfK;

    @Value("${hybrid-search.use-reranking:true}")
    private boolean useReranking;

    /**
     * Performs hybrid search combining vector and keyword results using RRF reactively.
     * Optionally applies cross-encoder reranking for improved relevance.
     * 
     * @param query the search query
     * @param topK number of final results to return
     * @param filters optional metadata filters for vector search
     * @return Mono of list of documents ranked by RRF score (and optionally reranked)
     */
    public Mono<List<Document>> hybridSearchAsync(String query, int topK, Map<String, Object> filters) {
        log.debug("Performing async hybrid search for query: '{}' with topK={}", query, topK);
        
        int finalTopK = topK <= 0 ? 10 : topK;
        int retrievalK = finalTopK * 2;

        // Execute vector and keyword searches in parallel
        Mono<List<Document>> vectorResultsMono = retrievalService.retrieveAsync(query, retrievalK, filters);
        Mono<List<KeywordSearchService.SearchResult>> keywordResultsMono = 
            keywordSearchService.searchAsync(query, retrievalK);

        return Mono.zip(vectorResultsMono, keywordResultsMono)
            .flatMap(tuple -> {
                List<Document> vectorResults = tuple.getT1();
                List<KeywordSearchService.SearchResult> keywordResults = tuple.getT2();
                
                log.debug("Parallel search completed: {} vector + {} keyword results", 
                    vectorResults.size(), keywordResults.size());
                
                // Apply RRF fusion
                List<Document> fusedResults = applyRRF(vectorResults, keywordResults, retrievalK);
                
                // Apply reranking if enabled
                if (useReranking) {
                    return rerankerService.rerankAsync(query, fusedResults, finalTopK);
                } else {
                    return Mono.just(fusedResults.stream().limit(finalTopK).collect(Collectors.toList()));
                }
            })
            .doOnSuccess(results -> 
                log.info("Async hybrid search returned {} results", results.size()))
            .onErrorResume(error -> {
                log.error("Error during async hybrid search, falling back to vector search only", error);
                return retrievalService.retrieveAsync(query, finalTopK, filters);
            });
    }

    /**
     * Performs hybrid search combining vector and keyword results using RRF (synchronous).
     * Optionally applies cross-encoder reranking for improved relevance.
     * 
     * @param query the search query
     * @param topK number of final results to return
     * @param filters optional metadata filters for vector search
     * @return list of documents ranked by RRF score (and optionally reranked)
     */
    public List<Document> hybridSearch(String query, int topK, Map<String, Object> filters) {
        log.debug("Performing hybrid search for query: '{}' with topK={}", query, topK);
        
        if (topK <= 0) {
            topK = 10;
        }

        // Retrieve more results initially for better fusion and reranking (2x topK)
        int retrievalK = topK * 2;

        try {
            // 1. Perform vector similarity search
            log.debug("Executing vector search with k={}", retrievalK);
            List<Document> vectorResults = retrievalService.retrieve(query, retrievalK, filters);
            
            // 2. Perform keyword search
            log.debug("Executing keyword search with k={}", retrievalK);
            List<KeywordSearchService.SearchResult> keywordResults = 
                keywordSearchService.search(query, retrievalK);
            
            // 3. Apply Reciprocal Rank Fusion
            log.debug("Applying RRF with k={}, vectorWeight={}, keywordWeight={}", 
                    rrfK, vectorWeight, keywordWeight);
            List<Document> fusedResults = applyRRF(vectorResults, keywordResults, retrievalK);
            
            // 4. Apply cross-encoder reranking if enabled
            List<Document> finalResults;
            if (useReranking) {
                log.debug("Applying cross-encoder reranking to top {} results", fusedResults.size());
                finalResults = rerankerService.rerank(query, fusedResults, topK);
            } else {
                finalResults = fusedResults.stream().limit(topK).collect(Collectors.toList());
            }
            
            log.info("Hybrid search returned {} results (from {} vector + {} keyword, reranking={})", 
                    finalResults.size(), vectorResults.size(), keywordResults.size(), useReranking);
            
            return finalResults;
            
        } catch (Exception e) {
            log.error("Error during hybrid search, falling back to vector search only", e);
            // Fallback to vector search only
            return retrievalService.retrieve(query, topK, filters);
        }
    }

    /**
     * Applies Reciprocal Rank Fusion (RRF) algorithm to merge ranked lists.
     * 
     * RRF formula: score(d) = Σ (1 / (k + rank(d)))
     * where k is a constant (typically 60) and rank is the position in the list.
     * 
     * We also apply weights to balance vector vs keyword contributions.
     * 
     * @param vectorResults results from vector search
     * @param keywordResults results from keyword search
     * @param topK number of final results
     * @return merged and reranked results
     */
    private List<Document> applyRRF(
            List<Document> vectorResults,
            List<KeywordSearchService.SearchResult> keywordResults,
            int topK) {
        
        // Map to store RRF scores by document ID
        Map<String, Double> rrfScores = new HashMap<>();
        Map<String, Document> documentMap = new HashMap<>();
        
        // Process vector results
        for (int i = 0; i < vectorResults.size(); i++) {
            Document doc = vectorResults.get(i);
            String docId = doc.getId();
            int rank = i + 1; // rank starts at 1
            
            // RRF score with vector weight
            double score = vectorWeight / (rrfK + rank);
            rrfScores.put(docId, rrfScores.getOrDefault(docId, 0.0) + score);
            documentMap.put(docId, doc);
            
            log.trace("Vector result {}: docId={}, rank={}, score={}", 
                    i, docId, rank, score);
        }
        
        // Process keyword results
        for (int i = 0; i < keywordResults.size(); i++) {
            KeywordSearchService.SearchResult result = keywordResults.get(i);
            String docId = result.id();
            int rank = i + 1;
            
            // RRF score with keyword weight
            double score = keywordWeight / (rrfK + rank);
            rrfScores.put(docId, rrfScores.getOrDefault(docId, 0.0) + score);
            
            // If document not in vector results, create from keyword result
            if (!documentMap.containsKey(docId)) {
                Document doc = createDocumentFromKeywordResult(result);
                documentMap.put(docId, doc);
            }
            
            log.trace("Keyword result {}: docId={}, rank={}, score={}", 
                    i, docId, rank, score);
        }
        
        // Sort by RRF score and return top K
        List<Document> rankedResults = rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(entry -> {
                    Document doc = documentMap.get(entry.getKey());
                    // Add RRF score to metadata
                    Map<String, Object> metadata = new HashMap<>(doc.getMetadata());
                    metadata.put("rrf_score", entry.getValue());
                    return new Document(doc.getId(), doc.getContent(), metadata);
                })
                .collect(Collectors.toList());
        
        log.debug("RRF fusion produced {} unique documents from {} total results", 
                rankedResults.size(), rrfScores.size());
        
        return rankedResults;
    }

    /**
     * Creates a Spring AI Document from a keyword search result.
     * 
     * @param result the keyword search result
     * @return Spring AI Document
     */
    private Document createDocumentFromKeywordResult(KeywordSearchService.SearchResult result) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("title", result.title());
        metadata.put("source", result.source());
        metadata.put("keyword_score", result.score());
        
        return new Document(result.id(), result.content(), metadata);
    }

    /**
     * Convenience method for hybrid search with default topK=10 and no filters.
     */
    public List<Document> hybridSearch(String query) {
        return hybridSearch(query, 10, null);
    }

    /**
     * Convenience method for async hybrid search with default topK=10 and no filters.
     */
    public Mono<List<Document>> hybridSearchAsync(String query) {
        return hybridSearchAsync(query, 10, null);
    }
}
