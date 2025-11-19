package com.support.assistant.agent;

import com.support.assistant.model.dto.QueryPlan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Service that wraps agent execution with retry logic using Spring @Retryable.
 * Provides automatic retry with exponential backoff and fallback recovery methods.
 */
@Service
@Slf4j
public class RetryableAgentExecutor {
    
    private final PlannerAgent plannerAgent;
    private final RetrieverAgent retrieverAgent;
    private final SynthesizerAgent synthesizerAgent;
    private final ValidatorAgent validatorAgent;
    private final FallbackStrategy fallbackStrategy;
    
    public RetryableAgentExecutor(
            PlannerAgent plannerAgent,
            RetrieverAgent retrieverAgent,
            SynthesizerAgent synthesizerAgent,
            ValidatorAgent validatorAgent,
            FallbackStrategy fallbackStrategy) {
        this.plannerAgent = plannerAgent;
        this.retrieverAgent = retrieverAgent;
        this.synthesizerAgent = synthesizerAgent;
        this.validatorAgent = validatorAgent;
        this.fallbackStrategy = fallbackStrategy;
    }
    
    /**
     * Execute planner agent with retry logic.
     */
    @Retryable(
        retryFor = {AgentExecutionException.class, RuntimeException.class},
        maxAttempts = 3,
        backoff = @Backoff(
            delay = 1000,
            multiplier = 2.0,
            maxDelay = 10000
        )
    )
    public QueryPlan executePlannerWithRetry(String query, AgentContext context) {
        log.debug("Executing planner agent (with retry support)");
        try {
            return plannerAgent.execute(query, context).block();
        } catch (Exception e) {
            log.error("Planner agent execution failed", e);
            throw new AgentExecutionException("Planner agent failed", e);
        }
    }
    
    /**
     * Recover from planner failures using fallback strategy.
     */
    @Recover
    public QueryPlan recoverFromPlannerFailure(
            AgentExecutionException e, 
            String query, 
            AgentContext context) {
        log.warn("Recovering from planner failure using fallback");
        return fallbackStrategy.planningFallback(query, e);
    }
    
    /**
     * Execute retriever agent with retry logic.
     */
    @Retryable(
        retryFor = {AgentExecutionException.class, RuntimeException.class},
        maxAttempts = 3,
        backoff = @Backoff(
            delay = 1000,
            multiplier = 2.0,
            maxDelay = 10000
        )
    )
    public List<Document> executeRetrieverWithRetry(
            RetrievalRequest request, 
            AgentContext context) {
        log.debug("Executing retriever agent (with retry support)");
        try {
            RetrievalResponse response = retrieverAgent.execute(request, context).block();
            return response != null ? response.documents() : List.of();
        } catch (Exception e) {
            log.error("Retriever agent execution failed", e);
            throw new AgentExecutionException("Retriever agent failed", e);
        }
    }
    
    /**
     * Recover from retriever failures using fallback strategy.
     */
    @Recover
    public List<Document> recoverFromRetrieverFailure(
            AgentExecutionException e,
            RetrievalRequest request,
            AgentContext context) {
        log.warn("Recovering from retriever failure using fallback");
        return fallbackStrategy.retrievalFallback(request.query(), e);
    }
    
    /**
     * Execute synthesizer agent with retry logic.
     */
    @Retryable(
        retryFor = {AgentExecutionException.class, RuntimeException.class},
        maxAttempts = 3,
        backoff = @Backoff(
            delay = 1000,
            multiplier = 2.0,
            maxDelay = 10000
        )
    )
    public SynthesisResponse executeSynthesizerWithRetry(
            SynthesisRequest request,
            AgentContext context) {
        log.debug("Executing synthesizer agent (with retry support)");
        try {
            return synthesizerAgent.execute(request, context).block();
        } catch (Exception e) {
            log.error("Synthesizer agent execution failed", e);
            throw new AgentExecutionException("Synthesizer agent failed", e);
        }
    }
    
    /**
     * Recover from synthesizer failures using fallback strategy.
     */
    @Recover
    public SynthesisResponse recoverFromSynthesizerFailure(
            AgentExecutionException e,
            SynthesisRequest request,
            AgentContext context) {
        log.warn("Recovering from synthesizer failure using fallback");
        return fallbackStrategy.generationFallback(request.query(), e);
    }
    
    /**
     * Execute validator agent with retry logic.
     */
    @Retryable(
        retryFor = {AgentExecutionException.class, RuntimeException.class},
        maxAttempts = 3,
        backoff = @Backoff(
            delay = 500,
            multiplier = 2.0,
            maxDelay = 5000
        )
    )
    public ValidationResult executeValidatorWithRetry(
            ValidationRequest request,
            AgentContext context) {
        log.debug("Executing validator agent (with retry support)");
        try {
            return validatorAgent.execute(request, context).block();
        } catch (Exception e) {
            log.error("Validator agent execution failed", e);
            throw new AgentExecutionException("Validator agent failed", e);
        }
    }
    
    /**
     * Recover from validator failures using fallback strategy.
     */
    @Recover
    public ValidationResult recoverFromValidatorFailure(
            AgentExecutionException e,
            ValidationRequest request,
            AgentContext context) {
        log.warn("Recovering from validator failure using fallback");
        return fallbackStrategy.validationFallback(request.response(), e);
    }
    
    /**
     * Execute compression with retry logic (optional operation).
     */
    @Retryable(
        retryFor = {RuntimeException.class},
        maxAttempts = 2,
        backoff = @Backoff(delay = 500, multiplier = 2.0)
    )
    public String executeCompressionWithRetry(
            List<Document> documents,
            String query,
            AgentContext context) {
        log.debug("Executing compression (with retry support)");
        try {
            // Simple compression for now - concatenate documents
            StringBuilder compressed = new StringBuilder();
            for (Document doc : documents) {
                compressed.append(doc.getContent()).append("\n\n");
            }
            return compressed.toString();
        } catch (Exception e) {
            log.error("Compression failed", e);
            throw new RuntimeException("Compression failed", e);
        }
    }
    
    /**
     * Recover from compression failures using fallback strategy.
     */
    @Recover
    public String recoverFromCompressionFailure(
            RuntimeException e,
            List<Document> documents,
            String query,
            AgentContext context) {
        log.warn("Recovering from compression failure using fallback");
        return fallbackStrategy.compressionFallback(documents, e);
    }
}
