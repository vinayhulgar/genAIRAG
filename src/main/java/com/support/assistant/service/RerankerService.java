package com.support.assistant.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for reranking documents using cross-encoder models.
 * Cross-encoders score query-document pairs directly for better relevance.
 * Supports reactive async execution with Mono/Flux.
 * 
 * This implementation provides a scoring mechanism that can be backed by:
 * - External reranking API (e.g., Cohere Rerank, Jina Reranker)
 * - Local ONNX model
 * - Python microservice with cross-encoder model
 * 
 * For now, implements a semantic similarity-based reranking as a baseline.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RerankerService {

    private final EmbeddingService embeddingService;

    @Value("${reranker.enabled:true}")
    private boolean rerankerEnabled;

    @Value("${reranker.top-k:10}")
    private int rerankerTopK;

    /**
     * Reranks documents using cross-encoder scoring reactively.
     * Takes top 20 results and reranks to final top 10.
     * 
     * @param query the search query
     * @param documents list of candidate documents (typically top 20)
     * @param finalTopK number of final results after reranking
     * @return Mono of reranked list of documents
     */
    public Mono<List<Document>> rerankAsync(String query, List<Document> documents, int finalTopK) {
        if (!rerankerEnabled) {
            log.debug("Reranker disabled, returning original results");
            return Mono.just(documents.stream().limit(finalTopK).collect(Collectors.toList()));
        }

        if (documents.isEmpty()) {
            return Mono.just(documents);
        }

        log.debug("Reranking {} documents to top {} asynchronously", documents.size(), finalTopK);

        // Score all documents in parallel
        return Flux.fromIterable(documents)
            .flatMap(doc -> scoreQueryDocumentPairAsync(query, doc)
                .map(score -> new ScoredDocument(doc, score)))
            .collectList()
            .map(scoredDocs -> {
                // Sort by reranking score and return top K
                List<Document> rerankedDocs = scoredDocs.stream()
                    .sorted(Comparator.comparingDouble(ScoredDocument::score).reversed())
                    .limit(finalTopK)
                    .map(sd -> {
                        // Add reranking score to metadata
                        Map<String, Object> metadata = new HashMap<>(sd.document().getMetadata());
                        metadata.put("rerank_score", sd.score());
                        return new Document(
                            sd.document().getId(),
                            sd.document().getContent(),
                            metadata
                        );
                    })
                    .collect(Collectors.toList());

                log.info("Async reranked {} documents to top {}", documents.size(), rerankedDocs.size());
                return rerankedDocs;
            })
            .onErrorResume(error -> {
                log.error("Error during async reranking, returning original results", error);
                return Mono.just(documents.stream().limit(finalTopK).collect(Collectors.toList()));
            });
    }

    /**
     * Reranks documents using cross-encoder scoring (synchronous).
     * Takes top 20 results and reranks to final top 10.
     * 
     * @param query the search query
     * @param documents list of candidate documents (typically top 20)
     * @param finalTopK number of final results after reranking
     * @return reranked list of documents
     */
    public List<Document> rerank(String query, List<Document> documents, int finalTopK) {
        if (!rerankerEnabled) {
            log.debug("Reranker disabled, returning original results");
            return documents.stream().limit(finalTopK).collect(Collectors.toList());
        }

        if (documents.isEmpty()) {
            return documents;
        }

        log.debug("Reranking {} documents to top {}", documents.size(), finalTopK);

        try {
            // Score each query-document pair
            List<ScoredDocument> scoredDocs = new ArrayList<>();
            
            for (Document doc : documents) {
                double score = scoreQueryDocumentPair(query, doc);
                scoredDocs.add(new ScoredDocument(doc, score));
                log.trace("Document {} scored: {}", doc.getId(), score);
            }

            // Sort by reranking score and return top K
            List<Document> rerankedDocs = scoredDocs.stream()
                    .sorted(Comparator.comparingDouble(ScoredDocument::score).reversed())
                    .limit(finalTopK)
                    .map(sd -> {
                        // Add reranking score to metadata
                        Map<String, Object> metadata = new HashMap<>(sd.document().getMetadata());
                        metadata.put("rerank_score", sd.score());
                        return new Document(
                                sd.document().getId(),
                                sd.document().getContent(),
                                metadata
                        );
                    })
                    .collect(Collectors.toList());

            log.info("Reranked {} documents to top {}", documents.size(), rerankedDocs.size());
            return rerankedDocs;

        } catch (Exception e) {
            log.error("Error during reranking, returning original results", e);
            return documents.stream().limit(finalTopK).collect(Collectors.toList());
        }
    }

    /**
     * Scores a query-document pair using cross-encoder approach reactively.
     * 
     * @param query the query text
     * @param document the document to score
     * @return Mono of relevance score (higher is better)
     */
    private Mono<Double> scoreQueryDocumentPairAsync(String query, Document document) {
        // Generate embeddings for query and document in parallel
        Mono<float[]> queryEmbeddingMono = embeddingService.embedQuery(query);
        Mono<float[]> docEmbeddingMono = embeddingService.embedQuery(document.getContent());

        return Mono.zip(queryEmbeddingMono, docEmbeddingMono)
            .map(tuple -> {
                float[] queryEmbedding = tuple.getT1();
                float[] docEmbedding = tuple.getT2();

                if (queryEmbedding == null || docEmbedding == null) {
                    log.warn("Failed to generate embeddings for scoring");
                    return 0.0;
                }

                // Calculate cosine similarity
                double similarity = cosineSimilarity(queryEmbedding, docEmbedding);
                
                // Apply length penalty to favor more comprehensive documents
                double lengthBoost = Math.min(1.0, document.getContent().length() / 1000.0);
                
                return similarity * (0.9 + 0.1 * lengthBoost);
            })
            .onErrorReturn(0.0);
    }

    /**
     * Scores a query-document pair using cross-encoder approach (synchronous).
     * 
     * Current implementation uses semantic similarity as a baseline.
     * This can be replaced with actual cross-encoder model inference:
     * - Call external API (Cohere Rerank, Jina AI)
     * - Use ONNX Runtime with cross-encoder model
     * - Call Python microservice with transformers library
     * 
     * @param query the query text
     * @param document the document to score
     * @return relevance score (higher is better)
     */
    private double scoreQueryDocumentPair(String query, Document document) {
        try {
            // Generate embeddings for query and document
            float[] queryEmbedding = embeddingService.embedQuery(query).block();
            float[] docEmbedding = embeddingService.embedQuery(document.getContent()).block();

            if (queryEmbedding == null || docEmbedding == null) {
                log.warn("Failed to generate embeddings for scoring");
                return 0.0;
            }

            // Calculate cosine similarity
            double similarity = cosineSimilarity(queryEmbedding, docEmbedding);
            
            // Apply length penalty to favor more comprehensive documents
            double lengthBoost = Math.min(1.0, document.getContent().length() / 1000.0);
            
            return similarity * (0.9 + 0.1 * lengthBoost);

        } catch (Exception e) {
            log.warn("Error scoring query-document pair: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * Calculates cosine similarity between two vectors.
     * 
     * @param vec1 first vector
     * @param vec2 second vector
     * @return cosine similarity score [-1, 1]
     */
    private double cosineSimilarity(float[] vec1, float[] vec2) {
        if (vec1.length != vec2.length) {
            throw new IllegalArgumentException("Vectors must have same dimension");
        }

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
            norm1 += vec1[i] * vec1[i];
            norm2 += vec2[i] * vec2[i];
        }

        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    /**
     * Internal record for storing document with its reranking score.
     */
    private record ScoredDocument(Document document, double score) {}
}
