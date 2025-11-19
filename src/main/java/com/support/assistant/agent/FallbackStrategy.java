package com.support.assistant.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Provides fallback strategies when agents fail.
 * Implements graceful degradation to ensure the system can still provide responses.
 */
@Component
@Slf4j
public class FallbackStrategy {
    
    /**
     * Fallback for planning failures.
     * Returns a simple query plan with the original query as a single sub-query.
     */
    public com.support.assistant.model.dto.QueryPlan planningFallback(String query, Throwable error) {
        log.warn("Planning failed, using fallback: single query plan. Error: {}", 
            error.getMessage());
        
        // Create a simple plan with just the original query
        var subQuery = new com.support.assistant.model.dto.SubQuery(
            0,
            query,
            Collections.emptyList(),
            com.support.assistant.model.dto.QueryType.FACTUAL
        );
        
        return new com.support.assistant.model.dto.QueryPlan(
            query,
            List.of(subQuery),
            List.of(0)
        );
    }
    
    /**
     * Fallback for retrieval failures.
     * Returns an empty document list, which will trigger a "no information" response.
     */
    public List<Document> retrievalFallback(String query, Throwable error) {
        log.warn("Retrieval failed, using fallback: empty document list. Error: {}", 
            error.getMessage());
        
        // Return empty list - synthesis will handle this gracefully
        return Collections.emptyList();
    }
    
    /**
     * Fallback for compression failures.
     * Returns the uncompressed concatenated content.
     */
    public String compressionFallback(List<Document> documents, Throwable error) {
        log.warn("Compression failed, using fallback: uncompressed content. Error: {}", 
            error.getMessage());
        
        // Skip compression and return raw concatenated content
        if (documents == null || documents.isEmpty()) {
            return "";
        }
        
        StringBuilder content = new StringBuilder();
        for (Document doc : documents) {
            content.append(doc.getContent()).append("\n\n");
        }
        
        return content.toString();
    }
    
    /**
     * Fallback for generation failures.
     * Returns a generic error message.
     */
    public SynthesisResponse generationFallback(String query, Throwable error) {
        log.error("Generation failed, using fallback: error message. Error: {}", 
            error.getMessage());
        
        String fallbackResponse = "I apologize, but I'm unable to generate a response " +
            "at this time due to a technical issue. Please try again later or " +
            "rephrase your question.";
        
        return new SynthesisResponse(
            fallbackResponse,
            Collections.emptyList(),
            0,
            "fallback"
        );
    }
    
    /**
     * Fallback for validation failures.
     * Returns a default validation result that allows the response to proceed.
     */
    public ValidationResult validationFallback(String response, Throwable error) {
        log.warn("Validation failed, using fallback: default valid result. Error: {}", 
            error.getMessage());
        
        // Return a permissive validation result
        return ValidationResult.builder()
            .valid(true)
            .confidenceScore(50.0) // Medium confidence
            .requiresHumanReview(true) // Flag for review since validation failed
            .reviewReason("Validation service unavailable")
            .build();
    }
    
    /**
     * Determine if an operation should be retried based on the error type.
     */
    public boolean shouldRetry(Throwable error, int attemptCount, int maxAttempts) {
        if (attemptCount >= maxAttempts) {
            return false;
        }
        
        // Don't retry on validation errors (bad input)
        if (error instanceof IllegalArgumentException) {
            log.debug("Not retrying IllegalArgumentException");
            return false;
        }
        
        // Don't retry on null pointer exceptions (programming errors)
        if (error instanceof NullPointerException) {
            log.debug("Not retrying NullPointerException");
            return false;
        }
        
        // Retry on agent execution exceptions and runtime exceptions
        return error instanceof AgentExecutionException || 
               error instanceof RuntimeException;
    }
    
    /**
     * Calculate backoff delay for retry attempts.
     * Uses exponential backoff with jitter.
     */
    public long calculateBackoffDelay(int attemptCount) {
        // Base delay: 1 second
        long baseDelay = 1000;
        
        // Exponential backoff: 1s, 2s, 4s, 8s, etc.
        long delay = baseDelay * (long) Math.pow(2, attemptCount - 1);
        
        // Add jitter (random 0-20% variation)
        double jitter = 0.8 + (Math.random() * 0.4); // 0.8 to 1.2
        delay = (long) (delay * jitter);
        
        // Cap at 10 seconds
        return Math.min(delay, 10000);
    }
}
