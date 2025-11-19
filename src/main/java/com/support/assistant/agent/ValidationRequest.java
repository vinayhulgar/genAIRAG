package com.support.assistant.agent;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * Request object for the ValidatorAgent.
 */
public record ValidationRequest(
    String response,
    List<Document> sources,
    String query
) {
    /**
     * Create a validation request
     */
    public static ValidationRequest of(String response, List<Document> sources, String query) {
        return new ValidationRequest(response, sources, query);
    }
}
