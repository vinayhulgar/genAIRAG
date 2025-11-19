package com.support.assistant.agent;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * Response object from the RetrieverAgent.
 */
public record RetrievalResponse(
    List<Document> documents,
    int hopsPerformed
) {
}
