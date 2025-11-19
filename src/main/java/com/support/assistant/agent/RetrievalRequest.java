package com.support.assistant.agent;

import java.util.Map;

/**
 * Request object for the RetrieverAgent.
 */
public record RetrievalRequest(
    String query,
    int topK,
    Map<String, Object> filters,
    boolean useMultiHop
) {
    /**
     * Create a simple retrieval request with just a query
     */
    public static RetrievalRequest of(String query) {
        return new RetrievalRequest(query, 10, null, false);
    }
    
    /**
     * Create a retrieval request with query and topK
     */
    public static RetrievalRequest of(String query, int topK) {
        return new RetrievalRequest(query, topK, null, false);
    }
    
    /**
     * Create a retrieval request with multi-hop enabled
     */
    public static RetrievalRequest withMultiHop(String query, int topK) {
        return new RetrievalRequest(query, topK, null, true);
    }
}
