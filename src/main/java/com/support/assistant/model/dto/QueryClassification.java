package com.support.assistant.model.dto;

/**
 * Result of query classification.
 * Contains the classified query type and confidence score.
 */
public record QueryClassification(
    QueryType queryType,
    double confidence,
    String reasoning
) {}
