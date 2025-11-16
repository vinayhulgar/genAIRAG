package com.support.assistant.model.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * Request DTO for query submission
 */
public record QueryRequest(
    @NotBlank(message = "Query cannot be blank")
    String query,
    String sessionId,
    Map<String, Object> context,
    boolean stream
) {}
