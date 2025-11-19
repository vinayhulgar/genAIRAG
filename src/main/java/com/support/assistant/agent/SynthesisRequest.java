package com.support.assistant.agent;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * Request object for the SynthesizerAgent.
 */
public record SynthesisRequest(
    String query,
    List<Document> documents,
    String compressedContext
) {
    /**
     * Create a synthesis request with query and documents
     */
    public static SynthesisRequest of(String query, List<Document> documents) {
        return new SynthesisRequest(query, documents, null);
    }
}
