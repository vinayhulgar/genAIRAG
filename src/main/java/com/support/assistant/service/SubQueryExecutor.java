package com.support.assistant.service;

import com.support.assistant.model.dto.QueryPlan;
import com.support.assistant.model.dto.SubQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service for executing sub-queries in order based on their dependencies.
 * Passes results from earlier queries as context to later ones.
 * Supports parallel execution of independent sub-queries using reactive patterns.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubQueryExecutor {

    private final RetrievalService retrievalService;
    private final HybridSearchService hybridSearchService;
    private final SynthesisService synthesisService;

    /**
     * Executes all sub-queries in the query plan with parallel execution for independent queries.
     * Results from earlier queries are passed as context to dependent queries.
     * 
     * @param queryPlan the query plan with sub-queries and execution order
     * @param filters optional metadata filters for retrieval
     * @return Mono of list of sub-query results
     */
    public Mono<List<SubQueryResult>> executeSubQueriesAsync(QueryPlan queryPlan, Map<String, Object> filters) {
        log.info("Executing query plan asynchronously with {} sub-queries", queryPlan.size());
        
        if (queryPlan.isSimple()) {
            log.debug("Simple query, executing single sub-query");
            return executeSingleQueryAsync(queryPlan.subQueries().get(0), filters)
                .map(List::of);
        }
        
        // Use concurrent map to store results as they complete
        Map<Integer, SubQueryResult> resultMap = new ConcurrentHashMap<>();
        
        // Group sub-queries by dependency level for parallel execution
        List<List<SubQuery>> executionLevels = groupByDependencyLevel(queryPlan);
        
        // Execute each level sequentially, but queries within a level in parallel
        return Flux.fromIterable(executionLevels)
            .concatMap(level -> executeQueriesInParallel(level, resultMap, filters))
            .collectList()
            .flatMap(allResults -> {
                // Flatten results and sort by execution order
                List<SubQueryResult> sortedResults = queryPlan.executionOrder().stream()
                    .map(resultMap::get)
                    .collect(Collectors.toList());
                
                log.info("All sub-queries executed successfully");
                return Mono.just(sortedResults);
            })
            .onErrorResume(error -> {
                log.error("Error executing sub-queries: {}", error.getMessage(), error);
                log.warn("Falling back to original query execution");
                return executeFallbackQueryAsync(queryPlan.originalQuery(), filters)
                    .map(List::of);
            });
    }

    /**
     * Groups sub-queries by dependency level for parallel execution.
     * Queries at the same level have no dependencies on each other.
     */
    private List<List<SubQuery>> groupByDependencyLevel(QueryPlan queryPlan) {
        List<List<SubQuery>> levels = new ArrayList<>();
        Map<Integer, Integer> queryLevels = new HashMap<>();
        
        // Calculate level for each query based on dependencies
        for (SubQuery subQuery : queryPlan.subQueries()) {
            int level = calculateLevel(subQuery, queryPlan.subQueries(), queryLevels);
            queryLevels.put(subQuery.id(), level);
        }
        
        // Group queries by level
        int maxLevel = queryLevels.values().stream().max(Integer::compareTo).orElse(0);
        for (int i = 0; i <= maxLevel; i++) {
            final int currentLevel = i;
            List<SubQuery> levelQueries = queryPlan.subQueries().stream()
                .filter(q -> queryLevels.get(q.id()) == currentLevel)
                .collect(Collectors.toList());
            if (!levelQueries.isEmpty()) {
                levels.add(levelQueries);
            }
        }
        
        log.debug("Grouped {} sub-queries into {} execution levels", 
            queryPlan.subQueries().size(), levels.size());
        
        return levels;
    }

    /**
     * Calculates the dependency level for a sub-query.
     */
    private int calculateLevel(SubQuery subQuery, List<SubQuery> allQueries, 
                              Map<Integer, Integer> queryLevels) {
        if (subQuery.dependencies().isEmpty()) {
            return 0;
        }
        
        int maxDepLevel = 0;
        for (Integer depId : subQuery.dependencies()) {
            Integer depLevel = queryLevels.get(depId);
            if (depLevel == null) {
                // Calculate dependency level recursively
                SubQuery depQuery = allQueries.stream()
                    .filter(q -> q.id() == depId)
                    .findFirst()
                    .orElse(null);
                if (depQuery != null) {
                    depLevel = calculateLevel(depQuery, allQueries, queryLevels);
                    queryLevels.put(depId, depLevel);
                }
            }
            if (depLevel != null) {
                maxDepLevel = Math.max(maxDepLevel, depLevel);
            }
        }
        
        return maxDepLevel + 1;
    }

    /**
     * Executes multiple queries in parallel at the same dependency level.
     */
    private Mono<List<SubQueryResult>> executeQueriesInParallel(
            List<SubQuery> queries,
            Map<Integer, SubQueryResult> resultMap,
            Map<String, Object> filters) {
        
        log.debug("Executing {} queries in parallel", queries.size());
        
        return Flux.fromIterable(queries)
            .flatMap(subQuery -> {
                String dependencyContext = buildDependencyContext(subQuery, resultMap);
                return executeSubQueryAsync(subQuery, dependencyContext, filters)
                    .doOnSuccess(result -> resultMap.put(subQuery.id(), result));
            })
            .collectList();
    }

    /**
     * Executes all sub-queries in the query plan sequentially (synchronous version).
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
     * Executes a single sub-query with optional dependency context asynchronously.
     */
    private Mono<SubQueryResult> executeSubQueryAsync(SubQuery subQuery, String dependencyContext, 
                                                      Map<String, Object> filters) {
        log.debug("Executing sub-query {} asynchronously: '{}'", subQuery.id(), subQuery.query());
        
        return Mono.fromCallable(() -> {
            // Enhance query with dependency context if available
            String enhancedQuery = enhanceQueryWithContext(subQuery.query(), dependencyContext);
            return enhancedQuery;
        })
        .flatMap(enhancedQuery -> 
            // Retrieve relevant documents using hybrid search
            hybridSearchService.hybridSearchAsync(enhancedQuery, 10, filters)
        )
        .flatMap(retrievedDocuments -> 
            // Synthesize response
            synthesisService.synthesizeAsync(subQuery.query(), retrievedDocuments)
                .map(synthesisResult -> new SubQueryResult(
                    subQuery.id(),
                    subQuery.query(),
                    synthesisResult.response(),
                    retrievedDocuments,
                    synthesisResult.sources(),
                    synthesisResult.tokensUsed(),
                    true // success
                ))
        )
        .onErrorResume(error -> {
            log.error("Error executing sub-query {}: {}", subQuery.id(), error.getMessage());
            return Mono.just(new SubQueryResult(
                subQuery.id(),
                subQuery.query(),
                "Failed to process this sub-query: " + error.getMessage(),
                List.of(),
                List.of(),
                0,
                false // failed
            ));
        });
    }

    /**
     * Executes a single sub-query with optional dependency context (synchronous).
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
     * Executes a single query asynchronously (for simple query plans).
     */
    private Mono<SubQueryResult> executeSingleQueryAsync(SubQuery subQuery, Map<String, Object> filters) {
        return executeSubQueryAsync(subQuery, null, filters);
    }

    /**
     * Executes a single query (for simple query plans).
     */
    private List<SubQueryResult> executeSingleQuery(SubQuery subQuery, Map<String, Object> filters) {
        SubQueryResult result = executeSubQuery(subQuery, null, filters);
        return List.of(result);
    }

    /**
     * Fallback execution when sub-query processing fails (async).
     * Executes the original query as a single unit.
     */
    private Mono<SubQueryResult> executeFallbackQueryAsync(String originalQuery, Map<String, Object> filters) {
        log.info("Executing fallback query asynchronously: '{}'", originalQuery);
        
        return hybridSearchService.hybridSearchAsync(originalQuery, 10, filters)
            .flatMap(retrievedDocuments -> 
                synthesisService.synthesizeAsync(originalQuery, retrievedDocuments)
                    .map(synthesisResult -> new SubQueryResult(
                        0,
                        originalQuery,
                        synthesisResult.response(),
                        retrievedDocuments,
                        synthesisResult.sources(),
                        synthesisResult.tokensUsed(),
                        true
                    ))
            )
            .onErrorResume(error -> {
                log.error("Fallback query execution failed: {}", error.getMessage());
                return Mono.error(new RuntimeException("Failed to execute query", error));
            });
    }

    /**
     * Fallback execution when sub-query processing fails (synchronous).
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
