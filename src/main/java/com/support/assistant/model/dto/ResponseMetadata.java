package com.support.assistant.model.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Metadata for query responses
 */
public record ResponseMetadata(
    int tokensUsed,
    int latencyMs,
    String modelUsed,
    Instant timestamp,
    Map<String, Object> additionalInfo
) {}
