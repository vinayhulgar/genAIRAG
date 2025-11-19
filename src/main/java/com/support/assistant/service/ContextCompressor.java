package com.support.assistant.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for compressing retrieved context to only relevant information.
 * Uses embedding similarity and semantic deduplication to reduce token usage.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContextCompressor {

    private final EmbeddingService embeddingService;
    private final TokenBudgetManager tokenBudgetManager;
    
    private static final int DEFAULT_MAX_TOKENS = TokenBudgetManager.DEFAULT_MAX_CONTEXT_TOKENS;
    private static final double RELEVANCE_THRESHOLD = 0.5;
    private static final double SIMILARITY_THRESHOLD = 0.85; // For deduplication

    /**
     * Compresses a list of documents to fit within token budget while maintaining relevance.
     *
     * @param documents list of retrieved documents
     * @param query the original query
     * @param maxTokens maximum tokens allowed (default: 4000)
     * @return compressed context result
     */
    public Mono<CompressionResult> compress(List<Document> documents, String query, int maxTokens) {
        if (maxTokens <= 0) {
            maxTokens = DEFAULT_MAX_TOKENS;
        }
        
        final int targetTokens = maxTokens;
        log.debug("Starting context compression for {} documents with max tokens: {}", 
                documents.size(), targetTokens);
        
        if (documents.isEmpty()) {
            return Mono.just(new CompressionResult("", 0.0, List.of(), 0, 0, ""));
        }
        
        // Get query embedding for relevance scoring
        return embeddingService.embedQuery(query)
            .flatMap(queryEmbedding -> {
                // Split documents into sentences and score them
                List<ScoredSentence> scoredSentences = new ArrayList<>();
                
                for (Document doc : documents) {
                    List<String> sentences = splitIntoSentences(doc.getContent());
                    
                    for (String sentence : sentences) {
                        if (sentence.trim().isEmpty()) {
                            continue;
                        }
                        
                        scoredSentences.add(new ScoredSentence(
                            sentence,
                            doc.getId(),
                            0.0, // Will be scored later
                            tokenBudgetManager.countTokens(sentence)
                        ));
                    }
                }
                
                // Score sentences by embedding similarity
                return scoreSentences(scoredSentences, queryEmbedding)
                    .map(scored -> {
                        // Sort by relevance score (descending)
                        scored.sort((a, b) -> Double.compare(b.score, a.score));
                        
                        // Filter by relevance threshold
                        List<ScoredSentence> relevant = scored.stream()
                            .filter(s -> s.score >= RELEVANCE_THRESHOLD)
                            .collect(Collectors.toList());
                        
                        if (relevant.isEmpty()) {
                            // If no sentences meet threshold, take top sentences
                            relevant = scored.stream()
                                .limit(Math.min(10, scored.size()))
                                .collect(Collectors.toList());
                        }
                        
                        // Remove redundant sentences
                        List<ScoredSentence> deduplicated = deduplicateSentences(relevant);
                        
                        // Select sentences within token budget
                        List<ScoredSentence> selected = selectWithinBudget(deduplicated, targetTokens);
                        
                        // Build compressed context
                        String compressedContext = selected.stream()
                            .map(s -> s.sentence)
                            .collect(Collectors.joining(" "));
                        
                        // Calculate metrics
                        int originalTokens = documents.stream()
                            .mapToInt(d -> tokenBudgetManager.countTokens(d.getContent()))
                            .sum();
                        int compressedTokens = tokenBudgetManager.countTokens(compressedContext);
                        double compressionRatio = originalTokens > 0 
                            ? (double) compressedTokens / originalTokens 
                            : 0.0;
                        
                        List<String> retainedDocIds = selected.stream()
                            .map(s -> s.documentId)
                            .distinct()
                            .collect(Collectors.toList());
                        
                        // Generate query ID for logging
                        String queryId = UUID.randomUUID().toString();
                        
                        // Log compression ratio for monitoring
                        tokenBudgetManager.logCompressionRatio(queryId, originalTokens, compressedTokens);
                        
                        log.info("Compression complete: {} -> {} tokens (ratio: {:.2f}), retained {} documents",
                            originalTokens, compressedTokens, compressionRatio, retainedDocIds.size());
                        
                        return new CompressionResult(
                            compressedContext,
                            compressionRatio,
                            retainedDocIds,
                            originalTokens,
                            compressedTokens,
                            queryId
                        );
                    });
            })
            .doOnError(e -> log.error("Error during context compression", e));
    }

    /**
     * Convenience method with default max tokens.
     */
    public Mono<CompressionResult> compress(List<Document> documents, String query) {
        return compress(documents, query, DEFAULT_MAX_TOKENS);
    }

    /**
     * Scores sentences by computing embedding similarity with query.
     */
    private Mono<List<ScoredSentence>> scoreSentences(List<ScoredSentence> sentences, float[] queryEmbedding) {
        if (sentences.isEmpty()) {
            return Mono.just(List.of());
        }
        
        // Create documents from sentences for batch embedding
        List<Document> sentenceDocs = sentences.stream()
            .map(s -> new Document(s.sentence))
            .collect(Collectors.toList());
        
        return embeddingService.embedDocuments(sentenceDocs)
            .map(embeddings -> {
                for (int i = 0; i < sentences.size(); i++) {
                    float[] sentenceEmbedding = embeddings.get(i);
                    double similarity = cosineSimilarity(queryEmbedding, sentenceEmbedding);
                    sentences.get(i).score = similarity;
                }
                return sentences;
            });
    }

    /**
     * Removes redundant sentences using semantic similarity.
     */
    private List<ScoredSentence> deduplicateSentences(List<ScoredSentence> sentences) {
        if (sentences.size() <= 1) {
            return sentences;
        }
        
        List<ScoredSentence> deduplicated = new ArrayList<>();
        deduplicated.add(sentences.get(0)); // Always keep the most relevant
        
        for (int i = 1; i < sentences.size(); i++) {
            ScoredSentence candidate = sentences.get(i);
            boolean isDuplicate = false;
            
            // Check similarity with already selected sentences
            for (ScoredSentence selected : deduplicated) {
                double similarity = simpleSimilarity(candidate.sentence, selected.sentence);
                if (similarity >= SIMILARITY_THRESHOLD) {
                    isDuplicate = true;
                    break;
                }
            }
            
            if (!isDuplicate) {
                deduplicated.add(candidate);
            }
        }
        
        log.debug("Deduplication: {} -> {} sentences", sentences.size(), deduplicated.size());
        return deduplicated;
    }

    /**
     * Selects sentences within token budget, prioritizing by relevance score.
     */
    private List<ScoredSentence> selectWithinBudget(List<ScoredSentence> sentences, int maxTokens) {
        List<ScoredSentence> selected = new ArrayList<>();
        int currentTokens = 0;
        
        for (ScoredSentence sentence : sentences) {
            if (currentTokens + sentence.tokenCount <= maxTokens) {
                selected.add(sentence);
                currentTokens += sentence.tokenCount;
            } else {
                // Check if we can fit partial content
                int remainingTokens = maxTokens - currentTokens;
                if (remainingTokens > 50) { // Only if meaningful space remains
                    // Truncate sentence to fit
                    String truncated = tokenBudgetManager.truncateToTokens(sentence.sentence, remainingTokens);
                    selected.add(new ScoredSentence(
                        truncated,
                        sentence.documentId,
                        sentence.score,
                        tokenBudgetManager.countTokens(truncated)
                    ));
                }
                break;
            }
        }
        
        return selected;
    }

    /**
     * Splits text into sentences using simple heuristics.
     */
    private List<String> splitIntoSentences(String text) {
        // Simple sentence splitting - can be enhanced with NLP libraries
        String[] sentences = text.split("(?<=[.!?])\\s+");
        return Arrays.stream(sentences)
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    }

    /**
     * Computes cosine similarity between two vectors.
     */
    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Vectors must have same dimension");
        }
        
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * Simple text similarity using Jaccard index for deduplication.
     */
    private double simpleSimilarity(String s1, String s2) {
        Set<String> words1 = new HashSet<>(Arrays.asList(s1.toLowerCase().split("\\s+")));
        Set<String> words2 = new HashSet<>(Arrays.asList(s2.toLowerCase().split("\\s+")));
        
        Set<String> intersection = new HashSet<>(words1);
        intersection.retainAll(words2);
        
        Set<String> union = new HashSet<>(words1);
        union.addAll(words2);
        
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    /**
     * Internal class to hold sentence with relevance score.
     */
    private static class ScoredSentence {
        String sentence;
        String documentId;
        double score;
        int tokenCount;
        
        ScoredSentence(String sentence, String documentId, double score, int tokenCount) {
            this.sentence = sentence;
            this.documentId = documentId;
            this.score = score;
            this.tokenCount = tokenCount;
        }
    }

    /**
     * Result of context compression operation.
     */
    public record CompressionResult(
        String compressedContext,
        double compressionRatio,
        List<String> retainedDocumentIds,
        int originalTokens,
        int compressedTokens,
        String queryId
    ) {}
}
