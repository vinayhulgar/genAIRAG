package com.support.assistant.model.dto;

import java.util.List;

/**
 * Represents a sub-query extracted from a complex query.
 * Contains the query text, dependencies, and type.
 */
public record SubQuery(
    int id,
    String query,
    List<Integer> dependencies,
    QueryType queryType
) {
    /**
     * Creates a SubQuery with no dependencies.
     */
    public SubQuery(int id, String query, QueryType queryType) {
        this(id, query, List.of(), queryType);
    }
}
