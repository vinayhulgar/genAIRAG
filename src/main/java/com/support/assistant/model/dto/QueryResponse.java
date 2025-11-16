package com.support.assistant.model.dto;

import java.util.List;

/**
 * Response DTO for query results
 */
public record QueryResponse(
    String response,
    List<Source> sources,
    double confidenceScore,
    ResponseMetadata metadata
) {}
