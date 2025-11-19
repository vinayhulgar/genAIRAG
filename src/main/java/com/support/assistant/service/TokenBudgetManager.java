package com.support.assistant.service;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing token budgets and counting tokens.
 * Provides token counting, budget enforcement, and compression ratio logging.
 */
@Service
@Slf4j
public class TokenBudgetManager {

    private final EncodingRegistry encodingRegistry = Encodings.newDefaultEncodingRegistry();
    private final Encoding encoding = encodingRegistry.getEncoding(EncodingType.CL100K_BASE);
    
    // Track compression ratios for monitoring
    private final Map<String, CompressionMetrics> compressionMetrics = new ConcurrentHashMap<>();
    
    public static final int DEFAULT_MAX_CONTEXT_TOKENS = 4000;
    public static final int DEFAULT_MAX_RESPONSE_TOKENS = 1000;
    public static final int SAFETY_MARGIN_TOKENS = 100;

    /**
     * Counts tokens in a text string.
     *
     * @param text the text to count tokens for
     * @return number of tokens
     */
    public int countTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        
        try {
            return encoding.countTokens(text);
        } catch (Exception e) {
            log.warn("Error counting tokens, falling back to word-based estimation", e);
            // Fallback: rough estimation (1 token ≈ 0.75 words)
            return (int) Math.ceil(text.split("\\s+").length / 0.75);
        }
    }

    /**
     * Counts tokens in multiple text strings.
     *
     * @param texts list of texts
     * @return total number of tokens
     */
    public int countTokens(List<String> texts) {
        return texts.stream()
            .mapToInt(this::countTokens)
            .sum();
    }

    /**
     * Checks if text fits within token budget.
     *
     * @param text the text to check
     * @param maxTokens maximum allowed tokens
     * @return true if within budget, false otherwise
     */
    public boolean fitsWithinBudget(String text, int maxTokens) {
        int tokenCount = countTokens(text);
        boolean fits = tokenCount <= maxTokens;
        
        if (!fits) {
            log.debug("Text exceeds budget: {} tokens > {} max", tokenCount, maxTokens);
        }
        
        return fits;
    }

    /**
     * Enforces token budget by truncating text if necessary.
     *
     * @param text the text to enforce budget on
     * @param maxTokens maximum allowed tokens
     * @return text truncated to fit within budget
     */
    public String enforceTokenBudget(String text, int maxTokens) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        int tokenCount = countTokens(text);
        
        if (tokenCount <= maxTokens) {
            log.debug("Text within budget: {} tokens <= {} max", tokenCount, maxTokens);
            return text;
        }
        
        log.info("Enforcing token budget: truncating from {} to {} tokens", tokenCount, maxTokens);
        return truncateToTokens(text, maxTokens);
    }

    /**
     * Truncates text to fit within specified token limit.
     *
     * @param text the text to truncate
     * @param maxTokens maximum tokens
     * @return truncated text
     */
    public String truncateToTokens(String text, int maxTokens) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        try {
            IntArrayList tokens = encoding.encode(text);
            
            if (tokens.size() <= maxTokens) {
                return text;
            }
            
            // Truncate tokens using IntArrayList
            IntArrayList truncated = new IntArrayList();
            for (int i = 0; i < maxTokens; i++) {
                truncated.add(tokens.get(i));
            }
            
            String result = encoding.decode(truncated);
            
            log.debug("Truncated text from {} to {} tokens", tokens.size(), maxTokens);
            return result;
            
        } catch (Exception e) {
            log.error("Error truncating text, returning original", e);
            return text;
        }
    }

    /**
     * Calculates available tokens for context given total budget and response needs.
     *
     * @param totalBudget total token budget (e.g., model's context window)
     * @param responseTokens tokens reserved for response generation
     * @param systemPromptTokens tokens used by system prompt
     * @return available tokens for context
     */
    public int calculateAvailableContextTokens(int totalBudget, int responseTokens, int systemPromptTokens) {
        int available = totalBudget - responseTokens - systemPromptTokens - SAFETY_MARGIN_TOKENS;
        
        if (available < 0) {
            log.warn("Insufficient token budget: total={}, response={}, system={}", 
                totalBudget, responseTokens, systemPromptTokens);
            return 0;
        }
        
        log.debug("Available context tokens: {} (total: {}, response: {}, system: {}, safety: {})",
            available, totalBudget, responseTokens, systemPromptTokens, SAFETY_MARGIN_TOKENS);
        
        return available;
    }

    /**
     * Logs compression ratio for monitoring and analysis.
     *
     * @param queryId unique identifier for the query
     * @param originalTokens token count before compression
     * @param compressedTokens token count after compression
     */
    public void logCompressionRatio(String queryId, int originalTokens, int compressedTokens) {
        if (originalTokens <= 0) {
            log.warn("Invalid original token count: {}", originalTokens);
            return;
        }
        
        double ratio = (double) compressedTokens / originalTokens;
        double reductionPercent = (1.0 - ratio) * 100;
        
        CompressionMetrics metrics = new CompressionMetrics(
            originalTokens,
            compressedTokens,
            ratio,
            reductionPercent
        );
        
        compressionMetrics.put(queryId, metrics);
        
        log.info("Compression ratio for query {}: {:.2f} ({} -> {} tokens, {:.1f}% reduction)",
            queryId, ratio, originalTokens, compressedTokens, reductionPercent);
    }

    /**
     * Gets compression metrics for a specific query.
     *
     * @param queryId the query identifier
     * @return compression metrics or null if not found
     */
    public CompressionMetrics getCompressionMetrics(String queryId) {
        return compressionMetrics.get(queryId);
    }

    /**
     * Gets average compression ratio across all tracked queries.
     *
     * @return average compression ratio
     */
    public double getAverageCompressionRatio() {
        if (compressionMetrics.isEmpty()) {
            return 0.0;
        }
        
        double sum = compressionMetrics.values().stream()
            .mapToDouble(m -> m.ratio)
            .sum();
        
        return sum / compressionMetrics.size();
    }

    /**
     * Gets average token reduction percentage.
     *
     * @return average reduction percentage
     */
    public double getAverageReductionPercent() {
        if (compressionMetrics.isEmpty()) {
            return 0.0;
        }
        
        double sum = compressionMetrics.values().stream()
            .mapToDouble(m -> m.reductionPercent)
            .sum();
        
        return sum / compressionMetrics.size();
    }

    /**
     * Clears compression metrics (useful for testing or periodic cleanup).
     */
    public void clearMetrics() {
        compressionMetrics.clear();
        log.debug("Cleared compression metrics");
    }

    /**
     * Gets total number of tracked compression operations.
     *
     * @return count of tracked operations
     */
    public int getTrackedOperationsCount() {
        return compressionMetrics.size();
    }

    /**
     * Validates that context and response fit within model's token limit.
     *
     * @param contextTokens tokens in context
     * @param responseTokens tokens reserved for response
     * @param modelMaxTokens model's maximum context window
     * @return validation result
     */
    public TokenBudgetValidation validateBudget(int contextTokens, int responseTokens, int modelMaxTokens) {
        int totalRequired = contextTokens + responseTokens + SAFETY_MARGIN_TOKENS;
        boolean isValid = totalRequired <= modelMaxTokens;
        
        if (!isValid) {
            log.warn("Token budget validation failed: required {} > max {}", totalRequired, modelMaxTokens);
        }
        
        return new TokenBudgetValidation(
            isValid,
            contextTokens,
            responseTokens,
            totalRequired,
            modelMaxTokens,
            modelMaxTokens - totalRequired
        );
    }

    /**
     * Compression metrics for a single operation.
     */
    public record CompressionMetrics(
        int originalTokens,
        int compressedTokens,
        double ratio,
        double reductionPercent
    ) {}

    /**
     * Token budget validation result.
     */
    public record TokenBudgetValidation(
        boolean isValid,
        int contextTokens,
        int responseTokens,
        int totalRequired,
        int modelMaxTokens,
        int remainingTokens
    ) {}
}
