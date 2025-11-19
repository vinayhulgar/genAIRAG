package com.support.assistant.model.dto;

import java.util.List;

/**
 * Represents a query execution plan with sub-queries and their execution order.
 * Created by QueryPlanner for complex queries that need decomposition.
 */
public record QueryPlan(
    String originalQuery,
    List<SubQuery> subQueries,
    List<Integer> executionOrder
) {
    /**
     * Checks if this is a simple query (no decomposition needed).
     */
    public boolean isSimple() {
        return subQueries.isEmpty() || subQueries.size() == 1;
    }
    
    /**
     * Gets the number of sub-queries in the plan.
     */
    public int size() {
        return subQueries.size();
    }
}
