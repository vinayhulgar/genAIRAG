package com.support.assistant.model.dto;

/**
 * Source citation for responses
 */
public record Source(
    String documentId,
    String title,
    String excerpt,
    double relevanceScore
) {}
