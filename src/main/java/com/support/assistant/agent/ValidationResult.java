package com.support.assistant.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Result of response validation by the validator agent.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {
    
    /**
     * Whether the response is valid (no hallucinations detected)
     */
    private boolean valid;
    
    /**
     * Confidence score (0-100) indicating how confident we are in the response
     */
    private double confidenceScore;
    
    /**
     * List of claims that could not be verified against source documents
     */
    @Builder.Default
    private List<String> hallucinatedClaims = new ArrayList<>();
    
    /**
     * Detailed verification information for debugging
     */
    @Builder.Default
    private Map<String, Object> verificationDetails = new HashMap<>();
    
    /**
     * Whether the response requires human review
     */
    private boolean requiresHumanReview;
    
    /**
     * Reason for requiring human review
     */
    private String reviewReason;
    
    /**
     * Add a hallucinated claim
     */
    public void addHallucinatedClaim(String claim) {
        this.hallucinatedClaims.add(claim);
    }
    
    /**
     * Add verification detail
     */
    public void addVerificationDetail(String key, Object value) {
        this.verificationDetails.put(key, value);
    }
    
    /**
     * Check if validation passed the confidence threshold
     */
    public boolean passesThreshold(double threshold) {
        return this.confidenceScore >= threshold;
    }
}
