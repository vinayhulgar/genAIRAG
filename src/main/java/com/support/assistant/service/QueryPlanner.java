package com.support.assistant.service;

import com.support.assistant.model.dto.QueryPlan;
import com.support.assistant.model.dto.QueryType;
import com.support.assistant.model.dto.SubQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service for decomposing complex queries into sub-queries.
 * Uses LLM to identify multiple questions and their dependencies.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QueryPlanner {

    private final ChatClient chatClient;
    private final QueryClassifier queryClassifier;
    private final DependencyResolver dependencyResolver;

    private static final String DECOMPOSITION_PROMPT = """
        You are a query decomposition expert. Analyze the following user query and determine if it contains multiple questions that should be answered separately.
        
        If the query is simple (single question), return an empty list of sub-queries.
        If the query is complex (multiple questions), break it down into individual sub-queries.
        
        For each sub-query, identify:
        1. The specific question to answer
        2. Dependencies on other sub-queries (by index, 0-based)
        3. The type of query (FACTUAL, COMPARISON, PROCEDURAL, or ANALYTICAL)
        
        Rules:
        - Each sub-query should be self-contained and answerable independently (unless it has dependencies)
        - If a sub-query needs information from another sub-query, list that dependency
        - Number sub-queries starting from 0
        - Keep sub-queries focused and specific
        
        Examples:
        
        Query: "What is the return policy?"
        Result: Simple query, no decomposition needed (empty list)
        
        Query: "What is the return policy and how do I initiate a return?"
        Result: Two sub-queries:
        - Sub-query 0: "What is the return policy?" (FACTUAL, no dependencies)
        - Sub-query 1: "How do I initiate a return?" (PROCEDURAL, depends on 0)
        
        Query: "Compare Plan A and Plan B, and which one is better for small businesses?"
        Result: Two sub-queries:
        - Sub-query 0: "Compare Plan A and Plan B" (COMPARISON, no dependencies)
        - Sub-query 1: "Which plan is better for small businesses?" (ANALYTICAL, depends on 0)
        
        User Query: {query}
        
        Analyze this query and provide the decomposition.
        
        {format}
        """;

    /**
     * Creates a query plan by decomposing complex queries into sub-queries.
     *
     * @param query the user query to plan
     * @return QueryPlan with sub-queries and execution order
     */
    public QueryPlan planQuery(String query) {
        log.debug("Planning query: '{}'", query);
        
        try {
            // Create output converter for structured response
            BeanOutputConverter<DecompositionResult> outputConverter = 
                new BeanOutputConverter<>(DecompositionResult.class);
            
            // Build prompt with format instructions
            PromptTemplate promptTemplate = new PromptTemplate(DECOMPOSITION_PROMPT);
            String prompt = promptTemplate.create(Map.of(
                "query", query,
                "format", outputConverter.getFormat()
            )).getContents();
            
            // Call LLM with structured output
            String response = chatClient.prompt()
                .user(prompt)
                .call()
                .content();
            
            // Parse structured response
            DecompositionResult result = outputConverter.convert(response);
            
            // If no sub-queries or only one, treat as simple query
            if (result.subQueries() == null || result.subQueries().isEmpty()) {
                log.debug("Query is simple, no decomposition needed");
                return createSimpleQueryPlan(query);
            }
            
            // Convert to SubQuery objects
            List<SubQuery> subQueries = new ArrayList<>();
            for (SubQueryData data : result.subQueries()) {
                QueryType type = parseQueryType(data.queryType());
                SubQuery subQuery = new SubQuery(
                    data.id(),
                    data.query(),
                    data.dependencies() != null ? data.dependencies() : List.of(),
                    type
                );
                subQueries.add(subQuery);
            }
            
            log.debug("Query decomposed into {} sub-queries", subQueries.size());
            
            // Create initial plan and resolve execution order using DependencyResolver
            QueryPlan initialPlan = new QueryPlan(query, subQueries, List.of());
            return dependencyResolver.resolveExecutionOrder(initialPlan);
            
        } catch (Exception e) {
            log.warn("Error decomposing query, treating as simple query: {}", e.getMessage());
            // If decomposition fails, treat as simple query
            return createSimpleQueryPlan(query);
        }
    }

    /**
     * Creates a simple query plan for queries that don't need decomposition.
     */
    private QueryPlan createSimpleQueryPlan(String query) {
        // Classify the single query
        QueryType type = queryClassifier.classify(query).queryType();
        
        SubQuery singleQuery = new SubQuery(0, query, type);
        return new QueryPlan(
            query,
            List.of(singleQuery),
            List.of(0)
        );
    }

    /**
     * Parses query type string to enum, with fallback to FACTUAL.
     */
    private QueryType parseQueryType(String typeStr) {
        try {
            return QueryType.valueOf(typeStr.toUpperCase());
        } catch (Exception e) {
            log.warn("Invalid query type '{}', defaulting to FACTUAL", typeStr);
            return QueryType.FACTUAL;
        }
    }

    /**
     * Internal record for LLM structured output parsing.
     */
    public record DecompositionResult(
        List<SubQueryData> subQueries
    ) {}

    /**
     * Internal record for sub-query data from LLM.
     */
    public record SubQueryData(
        int id,
        String query,
        List<Integer> dependencies,
        String queryType
    ) {}
}
