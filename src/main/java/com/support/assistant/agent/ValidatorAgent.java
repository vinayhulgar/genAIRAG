package com.support.assistant.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent responsible for validating generated responses and detecting hallucinations.
 * Checks if the response is grounded in the source documents.
 */
@Component
@Slf4j
public class ValidatorAgent extends BaseAgent<ValidationRequest, ValidationResult> {
    
    @Value("${validation.confidence-threshold:70.0}")
    private double confidenceThreshold;
    
    @Value("${validation.enabled:true}")
    private boolean validationEnabled;
    
    public ValidatorAgent() {
        super("ValidatorAgent");
    }
    
    @Override
    protected Mono<ValidationResult> doExecute(ValidationRequest request, AgentContext context) {
        validateInput(request, context);
        
        if (!validationEnabled) {
            debugLog(context, "Validation disabled, returning default valid result");
            return Mono.just(createDefaultValidResult());
        }
        
        String response = request.response();
        List<Document> sources = request.sources();
        String query = request.query();
        
        debugLog(context, "Validating response against {} source documents", sources.size());
        
        return Mono.fromCallable(() -> {
            // Perform validation checks
            ValidationResult result = performValidation(response, sources, query, context);
            
            log.info("Validation complete: valid={}, confidence={}, hallucinations={}", 
                    result.isValid(), result.getConfidenceScore(), 
                    result.getHallucinatedClaims().size());
            
            // Store validation metadata in context
            context.setSharedData("validationScore", result.getConfidenceScore());
            context.setSharedData("hallucinationCount", result.getHallucinatedClaims().size());
            context.setSharedData("requiresReview", result.isRequiresHumanReview());
            
            return result;
        });
    }
    
    /**
     * Performs validation of the response against source documents.
     * This is a simplified implementation that will be enhanced with:
     * - Entailment checking using NLI models
     * - Fact extraction and verification
     * - Semantic similarity scoring
     * - LLM-as-judge validation
     */
    private ValidationResult performValidation(String response, List<Document> sources, 
                                              String query, AgentContext context) {
        
        // Basic validation: check if response contains "I don't have enough information"
        if (response.contains("I don't have enough information")) {
            debugLog(context, "Response indicates insufficient information");
            return ValidationResult.builder()
                    .valid(true)
                    .confidenceScore(100.0)
                    .requiresHumanReview(false)
                    .build();
        }
        
        // Calculate confidence based on multiple factors
        double semanticSimilarity = calculateSemanticSimilarity(response, sources);
        double sourceOverlap = calculateSourceOverlap(response, sources);
        double responseQuality = assessResponseQuality(response);
        
        // Weighted confidence score
        double confidenceScore = 
            0.4 * semanticSimilarity +
            0.4 * sourceOverlap +
            0.2 * responseQuality;
        
        // Detect potential hallucinations (simplified)
        List<String> hallucinatedClaims = detectHallucinations(response, sources);
        
        boolean isValid = hallucinatedClaims.isEmpty() && confidenceScore >= confidenceThreshold;
        boolean requiresReview = confidenceScore < confidenceThreshold || !hallucinatedClaims.isEmpty();
        
        String reviewReason = null;
        if (requiresReview) {
            if (confidenceScore < confidenceThreshold) {
                reviewReason = String.format("Low confidence score: %.2f (threshold: %.2f)", 
                        confidenceScore, confidenceThreshold);
            } else if (!hallucinatedClaims.isEmpty()) {
                reviewReason = String.format("Detected %d potential hallucinations", 
                        hallucinatedClaims.size());
            }
        }
        
        ValidationResult result = ValidationResult.builder()
                .valid(isValid)
                .confidenceScore(confidenceScore)
                .hallucinatedClaims(hallucinatedClaims)
                .requiresHumanReview(requiresReview)
                .reviewReason(reviewReason)
                .build();
        
        // Add detailed verification info
        result.addVerificationDetail("semanticSimilarity", semanticSimilarity);
        result.addVerificationDetail("sourceOverlap", sourceOverlap);
        result.addVerificationDetail("responseQuality", responseQuality);
        result.addVerificationDetail("sourceCount", sources.size());
        
        return result;
    }
    
    /**
     * Calculate semantic similarity between response and sources.
     * Simplified implementation - will be enhanced with embedding-based similarity.
     */
    private double calculateSemanticSimilarity(String response, List<Document> sources) {
        // Simplified: check if response contains key terms from sources
        String responseLower = response.toLowerCase();
        int matchCount = 0;
        int totalTerms = 0;
        
        for (Document doc : sources) {
            String[] terms = doc.getContent().toLowerCase().split("\\s+");
            for (String term : terms) {
                if (term.length() > 4) { // Only check meaningful terms
                    totalTerms++;
                    if (responseLower.contains(term)) {
                        matchCount++;
                    }
                }
            }
        }
        
        return totalTerms > 0 ? (matchCount * 100.0 / totalTerms) : 50.0;
    }
    
    /**
     * Calculate overlap between response and source documents.
     */
    private double calculateSourceOverlap(String response, List<Document> sources) {
        // Check if response references or quotes from sources
        String responseLower = response.toLowerCase();
        int overlapScore = 0;
        
        for (Document doc : sources) {
            String content = doc.getContent().toLowerCase();
            // Check for direct quotes or paraphrases (simplified)
            String[] sentences = content.split("[.!?]");
            for (String sentence : sentences) {
                if (sentence.length() > 20 && responseLower.contains(sentence.trim())) {
                    overlapScore += 20;
                }
            }
        }
        
        return Math.min(100.0, overlapScore);
    }
    
    /**
     * Assess the quality of the response.
     */
    private double assessResponseQuality(String response) {
        double score = 100.0;
        
        // Penalize very short responses
        if (response.length() < 50) {
            score -= 30;
        }
        
        // Penalize responses with uncertainty markers
        String lower = response.toLowerCase();
        if (lower.contains("maybe") || lower.contains("possibly") || 
            lower.contains("might be") || lower.contains("could be")) {
            score -= 20;
        }
        
        // Reward responses with citations
        if (response.contains("[Source:")) {
            score += 10;
        }
        
        return Math.max(0, Math.min(100, score));
    }
    
    /**
     * Detect potential hallucinations in the response.
     * Simplified implementation - will be enhanced with NLI models.
     */
    private List<String> detectHallucinations(String response, List<Document> sources) {
        List<String> hallucinations = new ArrayList<>();
        
        // Split response into sentences
        String[] sentences = response.split("[.!?]");
        
        // Combine all source content
        StringBuilder allSourceContent = new StringBuilder();
        for (Document doc : sources) {
            allSourceContent.append(doc.getContent().toLowerCase()).append(" ");
        }
        String sourceText = allSourceContent.toString();
        
        // Check each sentence for grounding in sources (simplified)
        for (String sentence : sentences) {
            String trimmed = sentence.trim();
            if (trimmed.length() > 20 && !trimmed.startsWith("I don't")) {
                // Check if sentence has any overlap with sources
                String[] words = trimmed.toLowerCase().split("\\s+");
                int matchedWords = 0;
                for (String word : words) {
                    if (word.length() > 4 && sourceText.contains(word)) {
                        matchedWords++;
                    }
                }
                
                // If less than 30% of words match sources, flag as potential hallucination
                if (words.length > 5 && matchedWords < words.length * 0.3) {
                    hallucinations.add(trimmed);
                }
            }
        }
        
        return hallucinations;
    }
    
    /**
     * Create a default valid result when validation is disabled.
     */
    private ValidationResult createDefaultValidResult() {
        return ValidationResult.builder()
                .valid(true)
                .confidenceScore(100.0)
                .requiresHumanReview(false)
                .build();
    }
    
    @Override
    protected void validateInput(ValidationRequest request, AgentContext context) {
        super.validateInput(request, context);
        if (request.response() == null || request.response().trim().isEmpty()) {
            throw new IllegalArgumentException("Response cannot be empty for validation");
        }
        if (request.sources() == null || request.sources().isEmpty()) {
            throw new IllegalArgumentException("Sources cannot be empty for validation");
        }
    }
    
    @Override
    public boolean canHandle(ValidationRequest request, AgentContext context) {
        return request != null 
            && request.response() != null 
            && !request.response().trim().isEmpty()
            && request.sources() != null 
            && !request.sources().isEmpty();
    }
}
