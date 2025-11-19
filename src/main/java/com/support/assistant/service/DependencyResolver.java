package com.support.assistant.service;

import com.support.assistant.model.dto.QueryPlan;
import com.support.assistant.model.dto.SubQuery;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service for resolving sub-query dependencies and determining execution order.
 * Implements topological sort to handle dependency graphs.
 */
@Service
@Slf4j
public class DependencyResolver {

    /**
     * Resolves dependencies and creates an execution order for sub-queries.
     * Uses topological sort (Kahn's algorithm) to determine the order.
     *
     * @param queryPlan the query plan with sub-queries
     * @return QueryPlan with resolved execution order
     * @throws IllegalArgumentException if cyclic dependencies are detected
     */
    public QueryPlan resolveExecutionOrder(QueryPlan queryPlan) {
        List<SubQuery> subQueries = queryPlan.subQueries();
        
        // Simple case: no sub-queries or single sub-query
        if (subQueries.isEmpty()) {
            return queryPlan;
        }
        if (subQueries.size() == 1) {
            return new QueryPlan(
                queryPlan.originalQuery(),
                subQueries,
                List.of(0)
            );
        }
        
        log.debug("Resolving execution order for {} sub-queries", subQueries.size());
        
        try {
            List<Integer> executionOrder = topologicalSort(subQueries);
            
            log.debug("Execution order resolved: {}", executionOrder);
            
            return new QueryPlan(
                queryPlan.originalQuery(),
                subQueries,
                executionOrder
            );
            
        } catch (CyclicDependencyException e) {
            log.warn("Cyclic dependency detected, falling back to sequential order: {}", e.getMessage());
            // Fallback to sequential order if cyclic dependencies detected
            return createSequentialPlan(queryPlan);
        }
    }

    /**
     * Performs topological sort using Kahn's algorithm.
     * 
     * @param subQueries list of sub-queries with dependencies
     * @return ordered list of sub-query IDs
     * @throws CyclicDependencyException if a cycle is detected
     */
    private List<Integer> topologicalSort(List<SubQuery> subQueries) throws CyclicDependencyException {
        int n = subQueries.size();
        
        // Build adjacency list and in-degree map
        Map<Integer, List<Integer>> adjacencyList = new HashMap<>();
        Map<Integer, Integer> inDegree = new HashMap<>();
        
        // Initialize
        for (SubQuery sq : subQueries) {
            adjacencyList.put(sq.id(), new ArrayList<>());
            inDegree.put(sq.id(), 0);
        }
        
        // Build graph
        for (SubQuery sq : subQueries) {
            for (Integer dependency : sq.dependencies()) {
                // Validate dependency exists
                if (dependency < 0 || dependency >= n) {
                    log.warn("Invalid dependency {} for sub-query {}, ignoring", dependency, sq.id());
                    continue;
                }
                
                // Add edge from dependency to current node
                adjacencyList.get(dependency).add(sq.id());
                inDegree.put(sq.id(), inDegree.get(sq.id()) + 1);
            }
        }
        
        // Find all nodes with in-degree 0 (no dependencies)
        Queue<Integer> queue = new LinkedList<>();
        for (Map.Entry<Integer, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
            }
        }
        
        // Perform topological sort
        List<Integer> result = new ArrayList<>();
        
        while (!queue.isEmpty()) {
            Integer current = queue.poll();
            result.add(current);
            
            // Reduce in-degree for all neighbors
            for (Integer neighbor : adjacencyList.get(current)) {
                inDegree.put(neighbor, inDegree.get(neighbor) - 1);
                
                // If in-degree becomes 0, add to queue
                if (inDegree.get(neighbor) == 0) {
                    queue.offer(neighbor);
                }
            }
        }
        
        // Check if all nodes were processed (no cycle)
        if (result.size() != n) {
            throw new CyclicDependencyException(
                "Cyclic dependency detected in sub-queries. Processed " + result.size() + " out of " + n
            );
        }
        
        return result;
    }

    /**
     * Creates a sequential execution plan as fallback.
     */
    private QueryPlan createSequentialPlan(QueryPlan queryPlan) {
        List<Integer> sequentialOrder = new ArrayList<>();
        for (int i = 0; i < queryPlan.subQueries().size(); i++) {
            sequentialOrder.add(i);
        }
        
        return new QueryPlan(
            queryPlan.originalQuery(),
            queryPlan.subQueries(),
            sequentialOrder
        );
    }

    /**
     * Validates that a query plan has no cyclic dependencies.
     * 
     * @param queryPlan the query plan to validate
     * @return true if valid, false if cyclic dependencies exist
     */
    public boolean isValid(QueryPlan queryPlan) {
        try {
            topologicalSort(queryPlan.subQueries());
            return true;
        } catch (CyclicDependencyException e) {
            return false;
        }
    }

    /**
     * Exception thrown when cyclic dependencies are detected.
     */
    public static class CyclicDependencyException extends Exception {
        public CyclicDependencyException(String message) {
            super(message);
        }
    }
}
