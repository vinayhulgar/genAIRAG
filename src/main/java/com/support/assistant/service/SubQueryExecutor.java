package com.support.assistant.service;

import com.support.assistant.model.dto.QueryPlan;
import com.support.assistant.model.dto.SubQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for executing sub-queries in order based on their dependencies.
 * Passes results from earlier queries as context to later ones.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubQueryExecutor {

    private final RetrievalService retrievalService;
    private final HybridSearchService hybridSearchService;
    private final SynthesisService synthesisService;

    /**
     * Executes all sub-queries in the query plan sequentially.
     * Results from earlier queries are passed as context to dependent queries.
     * 
     * @param queryPlan the query plan with sub-queries and execution order
     * @param filters optional metadata filters for retrieval
     * @return list of sub-query results in execution order
     */
    public List<SubQueryResult> executeSubQueries(QueryPlan queryPlan, Map<String, Object> filters) {
        log.info("Executing query plan with {} sub-queries", queryPlan.size());
        
        if (queryPlan.isSimple()) {
            log.debug("Simple query, executing single sub-query");
            return executeSingleQuery(queryPlan.subQueries().get(0), filters);
        }
        
        List<SubQueryResult> results = new ArrayList<>();
        Map<Integer, SubQueryResult> resultMap = new HashMap<>();
        
        try {
            // Execute sub-queries in dependency order
            for (Integer subQueryId : queryPlan.executionOrder()) {
                SubQuery subQuery = queryPlan.subQueries().get(subQueryId);
                log.debug("Executing sub-query {}: '{}'", subQueryId, subQuery.query());
                
                // Build context from dependent sub-queries
                String dependencyContext = buildDependencyContext(subQuery, resultMap);
                
                // Execute the sub-query
                SubQueryResult result = executeSubQuery(subQuery, dependencyContext, filters);
                
                // Store result for dependent queries
                resultMap.put(subQueryId, result);
                results.add(result);
                
                log.debug("Sub-query {} completed: {} documents retrieved", 
                    subQueryId, result.retrievedDocuments().size());
            }
            
            log.info("All sub-queries executed successfully");
            return results;
            
        } catch (Exception e) {
            log.error("Error executing sub-queries: {}", e.getMessage(), e);
            
            // Fallback: execute original query as single query
            log.warn("Falling back to original query execution");
            return executeFallbackQuery(queryPlan.originalQuery(), filters);
        }
    }

    /**
     * Executes a single sub-query with optional dependency context.
     */
    private SubQueryResult executeSubQuery(SubQuery subQuery, String dependencyContext, 
                                          Map<String, Object> filters) {
        try {
            // Enhance query with dependency context if available
            String enhancedQuery = enhanceQueryWithContext(subQuery.query(), dependencyContext);
            
            // Retrieve relevant documents using hybrid search
            List<Document> retrievedDocuments = hybridSearchService.hybridSearch(
                enhancedQuery,
                10, // top-k
                filters
            );
            
            // Synthesize response
            SynthesisService.SynthesisResult synthesisResult = synthesisService.synthesize(
                subQuery.query(), // Use original query for synthesis
                retrievedDocuments
            );
            
            return new SubQueryResult(
                subQuery.id(),
                subQuery.query(),
                synthesisResult.response(),
                retrievedDocuments,
                synthesisResult.sources(),
                synthesisResult.tokensUsed(),
                true // success
            );
            
        } catch (Exception e) {
            log.error("Error executing sub-query {}: {}", subQuery.id(), e.getMessage());
            
            // Return failed result
            return new SubQueryResult(
                subQuery.id(),
                subQuery.query(),
                "Failed to process this sub-query: " + e.getMessage(),
                List.of(),
                List.of(),
                0,
                false // failed
            );
        }
    }

    /**
     * Executes a single query (for simple query plans).
     */
    private List<SubQueryResult> executeSingleQuery(SubQuery subQuery, Map<String, Object> filters) {
        SubQueryResult result = executeSubQuery(subQuery, null, filters);
        return List.of(result);
    }

    /**
     * Fallback execution when sub-query processing fails.
     * Executes the original query as a single unit.
     */
    private List<SubQueryResult> executeFallbackQuery(String originalQuery, Map<String, Object> filters) {
        try {
            log.info("Executing fallback query: '{}'", originalQuery);
            
            List<Document> retrievedDocuments = hybridSearchService.hybridSearch(
                originalQuery,
                10,
                filters
            );
            
            SynthesisService.SynthesisResult synthesisResult = synthesisService.synthesize(
                originalQuery,
                retrievedDocuments
            );
            
            SubQueryResult result = new SubQueryResult(
                0,
                originalQuery,
                synthesisResult.response(),
                retrievedDocuments,
                synthesisResult.sources(),
                synthesisResult.tokensUsed(),
                true
            );
            
            return List.of(result);
            
        } catch (Exception e) {
            log.error("Fallback query execution failed: {}", e.getMessage());
            throw new RuntimeException("Failed to execute query", e);
        }
    }

    /**
     * Builds context string from dependent sub-query results.
     */
    private String buildDependencyContext(SubQuery subQuery, Map<Integer, SubQueryResult> resultMap) {
        if (subQuery.dependencies().isEmpty()) {
            return null;
        }
        
        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("Previous answers:\n\n");
        
        for (Integer depId : subQuery.dependencies()) {
            SubQueryResult depResult = resultMap.get(depId);
            if (depResult != null && depResult.success()) {
                contextBuilder.append("Q: ").append(depResult.query()).append("\n");
                contextBuilder.append("A: ").append(depResult.response()).append("\n\n");
            }
        }
        
        return contextBuilder.toString();
    }

    /**
     * Enhances a query with context from dependent queries.
     */
    private String enhanceQueryWithContext(String query, String dependencyContext) {
        if (dependencyContext == null || dependencyContext.isBlank()) {
            return query;
        }
        
        // Prepend context to help retrieval understand the full picture
        return dependencyContext + "\nCurrent question: " + query;
    }

    /**
     * Result of executing a single sub-query.
     */
    public record SubQueryResult(
        int id,
        String query,
        String response,
        List<Document> retrievedDocuments,
        List<com.support.assistant.model.dto.Source> sources,
        int tokensUsed,
        boolean success
    ) {}
}
