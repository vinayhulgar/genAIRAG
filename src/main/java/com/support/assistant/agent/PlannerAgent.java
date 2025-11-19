package com.support.assistant.agent;

import com.support.assistant.model.dto.QueryPlan;
import com.support.assistant.service.QueryPlanner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Agent responsible for query decomposition and planning.
 * Analyzes complex queries and breaks them into sub-queries with dependencies.
 */
@Component
@Slf4j
public class PlannerAgent extends BaseAgent<String, QueryPlan> {
    
    private final QueryPlanner queryPlanner;
    
    public PlannerAgent(QueryPlanner queryPlanner) {
        super("PlannerAgent");
        this.queryPlanner = queryPlanner;
    }
    
    @Override
    protected Mono<QueryPlan> doExecute(String query, AgentContext context) {
        validateInput(query, context);
        
        debugLog(context, "Analyzing and decomposing query: {}", query);
        
        return Mono.fromCallable(() -> {
            // Use QueryPlanner service to decompose the query
            QueryPlan plan = queryPlanner.planQuery(query);
            
            debugLog(context, "Query plan created with {} sub-queries", 
                    plan.subQueries().size());
            
            // Store plan metadata in context
            context.setSharedData("subQueryCount", plan.subQueries().size());
            context.setSharedData("isComplexQuery", plan.subQueries().size() > 1);
            
            return plan;
        });
    }
    
    @Override
    protected void validateInput(String query, AgentContext context) {
        super.validateInput(query, context);
        if (query.trim().isEmpty()) {
            throw new IllegalArgumentException("Query cannot be empty");
        }
    }
    
    @Override
    public boolean canHandle(String query, AgentContext context) {
        // PlannerAgent can handle any non-empty query
        return query != null && !query.trim().isEmpty();
    }
}
