package com.support.assistant.agent;

import com.support.assistant.model.dto.QueryPlan;
import com.support.assistant.model.dto.QueryRequest;
import com.support.assistant.model.dto.QueryResponse;
import com.support.assistant.model.dto.ResponseMetadata;
import com.support.assistant.model.dto.Source;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orchestrates the execution of multiple agents in a coordinated workflow.
 * Uses Spring State Machine to manage workflow states and transitions.
 */
@Service
@Slf4j
public class AgentOrchestrator {
    
    private final StateMachineFactory<WorkflowStatus, WorkflowEvent> stateMachineFactory;
    private final RetryableAgentExecutor retryableAgentExecutor;
    private final FallbackStrategy fallbackStrategy;
    
    public AgentOrchestrator(
            StateMachineFactory<WorkflowStatus, WorkflowEvent> stateMachineFactory,
            RetryableAgentExecutor retryableAgentExecutor,
            FallbackStrategy fallbackStrategy) {
        this.stateMachineFactory = stateMachineFactory;
        this.retryableAgentExecutor = retryableAgentExecutor;
        this.fallbackStrategy = fallbackStrategy;
    }
    
    /**
     * Execute the full agent workflow for a query.
     *
     * @param request The query request
     * @return A Mono containing the query response
     */
    public Mono<QueryResponse> executeWorkflow(QueryRequest request) {
        String executionId = UUID.randomUUID().toString();
        log.info("Starting workflow execution: {} for query: {}", 
            executionId, request.query());
        
        // Create agent context
        AgentContext context = AgentContext.builder()
            .executionId(executionId)
            .sessionId(request.sessionId())
            .maxRetries(3)
            .timeoutMs(30000)
            .debug(false)
            .build();
        
        // Create initial agent state
        AgentState state = AgentState.builder()
            .queryRequest(request)
            .status(WorkflowStatus.INITIALIZED)
            .startTime(Instant.now())
            .build();
        
        // Create and start state machine
        StateMachine<WorkflowStatus, WorkflowEvent> stateMachine = 
            stateMachineFactory.getStateMachine(executionId);
        
        // Store state and context in state machine extended state
        stateMachine.getExtendedState().getVariables().put("agentState", state);
        stateMachine.getExtendedState().getVariables().put("agentContext", context);
        stateMachine.getExtendedState().getVariables().put("maxRetries", 3);
        stateMachine.getExtendedState().getVariables().put("retryCount", 0);
        
        stateMachine.startReactively().block();
        
        // Execute workflow
        return executeWorkflowSteps(stateMachine, state, context)
            .doOnSuccess(response -> {
                log.info("Workflow execution completed: {}", executionId);
                stateMachine.stopReactively().block();
            })
            .doOnError(error -> {
                log.error("Workflow execution failed: {}", executionId, error);
                stateMachine.stopReactively().block();
            });
    }
    
    /**
     * Execute the workflow steps based on state machine transitions.
     */
    private Mono<QueryResponse> executeWorkflowSteps(
            StateMachine<WorkflowStatus, WorkflowEvent> stateMachine,
            AgentState state,
            AgentContext context) {
        
        // Start the workflow
        return Mono.fromRunnable(() -> stateMachine.sendEvent(WorkflowEvent.START))
            .then(executePlanning(stateMachine, state, context))
            .flatMap(queryPlan -> executeRetrieval(stateMachine, state, context, queryPlan))
            .flatMap(documents -> executeCompression(stateMachine, state, context, documents))
            .flatMap(compressedContext -> executeGeneration(stateMachine, state, context, compressedContext))
            .flatMap(synthesisResponse -> executeValidation(stateMachine, state, context, synthesisResponse))
            .flatMap(validationResult -> buildFinalResponse(stateMachine, state, context, validationResult));
    }
    
    /**
     * Execute planning stage with retry and fallback.
     */
    private Mono<QueryPlan> executePlanning(
            StateMachine<WorkflowStatus, WorkflowEvent> stateMachine,
            AgentState state,
            AgentContext context) {
        
        log.debug("Executing planning stage");
        state.setStatus(WorkflowStatus.PLANNING);
        
        return Mono.fromCallable(() -> 
                retryableAgentExecutor.executePlannerWithRetry(
                    state.getQueryRequest().query(), context))
            .doOnSuccess(queryPlan -> {
                state.setQueryPlan(queryPlan);
                stateMachine.sendEvent(WorkflowEvent.PLANNING_COMPLETE);
                log.info("Planning complete: {} sub-queries", queryPlan.subQueries().size());
            })
            .onErrorResume(error -> {
                log.error("Planning failed after retries, using fallback", error);
                QueryPlan fallbackPlan = fallbackStrategy.planningFallback(
                    state.getQueryRequest().query(), error);
                state.setQueryPlan(fallbackPlan);
                stateMachine.sendEvent(WorkflowEvent.PLANNING_COMPLETE);
                return Mono.just(fallbackPlan);
            });
    }
    
    /**
     * Execute retrieval stage with retry and fallback.
     */
    private Mono<List<Document>> executeRetrieval(
            StateMachine<WorkflowStatus, WorkflowEvent> stateMachine,
            AgentState state,
            AgentContext context,
            QueryPlan queryPlan) {
        
        log.debug("Executing retrieval stage");
        state.setStatus(WorkflowStatus.RETRIEVING);
        
        // Determine if multi-hop retrieval is needed
        boolean useMultiHop = queryPlan.subQueries().size() > 1;
        
        RetrievalRequest retrievalRequest = new RetrievalRequest(
            state.getQueryRequest().query(),
            10,
            state.getQueryRequest().context(),
            useMultiHop
        );
        
        return Mono.fromCallable(() -> 
                retryableAgentExecutor.executeRetrieverWithRetry(retrievalRequest, context))
            .doOnSuccess(documents -> {
                state.setRetrievedDocuments(documents);
                stateMachine.sendEvent(WorkflowEvent.RETRIEVAL_COMPLETE);
                log.info("Retrieval complete: {} documents", documents.size());
            })
            .onErrorResume(error -> {
                log.error("Retrieval failed after retries, using fallback", error);
                List<Document> fallbackDocs = fallbackStrategy.retrievalFallback(
                    state.getQueryRequest().query(), error);
                state.setRetrievedDocuments(fallbackDocs);
                stateMachine.sendEvent(WorkflowEvent.RETRIEVAL_COMPLETE);
                return Mono.just(fallbackDocs);
            });
    }
    
    /**
     * Execute compression stage with retry and fallback.
     * Compression is optional - if it fails, we skip it and proceed.
     */
    private Mono<String> executeCompression(
            StateMachine<WorkflowStatus, WorkflowEvent> stateMachine,
            AgentState state,
            AgentContext context,
            List<Document> documents) {
        
        log.debug("Executing compression stage");
        state.setStatus(WorkflowStatus.COMPRESSING);
        
        return Mono.fromCallable(() -> 
                retryableAgentExecutor.executeCompressionWithRetry(
                    documents, state.getQueryRequest().query(), context))
            .doOnSuccess(compressed -> {
                state.setCompressedContext(compressed);
                stateMachine.sendEvent(WorkflowEvent.COMPRESSION_COMPLETE);
                log.info("Compression complete");
            })
            .onErrorResume(error -> {
                log.warn("Compression failed after retries, skipping compression", error);
                // Compression is optional, use fallback (uncompressed content)
                String fallbackContent = fallbackStrategy.compressionFallback(documents, error);
                state.setCompressedContext(fallbackContent);
                state.addMetadata("compressionSkipped", true);
                stateMachine.sendEvent(WorkflowEvent.COMPRESSION_COMPLETE);
                return Mono.just(fallbackContent);
            });
    }
    
    /**
     * Execute generation stage with retry and fallback.
     */
    private Mono<SynthesisResponse> executeGeneration(
            StateMachine<WorkflowStatus, WorkflowEvent> stateMachine,
            AgentState state,
            AgentContext context,
            String compressedContext) {
        
        log.debug("Executing generation stage");
        state.setStatus(WorkflowStatus.GENERATING);
        
        SynthesisRequest synthesisRequest = new SynthesisRequest(
            state.getQueryRequest().query(),
            state.getRetrievedDocuments(),
            compressedContext
        );
        
        return Mono.fromCallable(() -> 
                retryableAgentExecutor.executeSynthesizerWithRetry(synthesisRequest, context))
            .doOnSuccess(synthesisResponse -> {
                state.setGeneratedResponse(synthesisResponse.response());
                stateMachine.sendEvent(WorkflowEvent.GENERATION_COMPLETE);
                log.info("Generation complete: {} tokens used", synthesisResponse.tokensUsed());
            })
            .onErrorResume(error -> {
                log.error("Generation failed after retries, using fallback", error);
                SynthesisResponse fallbackResponse = fallbackStrategy.generationFallback(
                    state.getQueryRequest().query(), error);
                state.setGeneratedResponse(fallbackResponse.response());
                state.addMetadata("generationFallback", true);
                stateMachine.sendEvent(WorkflowEvent.GENERATION_COMPLETE);
                return Mono.just(fallbackResponse);
            });
    }
    
    /**
     * Execute validation stage with retry and fallback.
     */
    private Mono<ValidationResult> executeValidation(
            StateMachine<WorkflowStatus, WorkflowEvent> stateMachine,
            AgentState state,
            AgentContext context,
            SynthesisResponse synthesisResponse) {
        
        log.debug("Executing validation stage");
        state.setStatus(WorkflowStatus.VALIDATING);
        
        ValidationRequest validationRequest = new ValidationRequest(
            synthesisResponse.response(),
            state.getRetrievedDocuments(),
            state.getQueryRequest().query()
        );
        
        return Mono.fromCallable(() -> 
                retryableAgentExecutor.executeValidatorWithRetry(validationRequest, context))
            .doOnSuccess(validationResult -> {
                state.setValidationResult(validationResult);
                
                // Check if we need to retry retrieval
                if (validationResult.isRequiresHumanReview() && 
                    validationResult.getConfidenceScore() < 50.0) {
                    
                    Integer retryCount = (Integer) stateMachine.getExtendedState()
                        .getVariables().get("retryCount");
                    if (retryCount == null) retryCount = 0;
                    
                    if (retryCount < 3) {
                        log.warn("Low confidence ({}), retrying retrieval (attempt {})", 
                            validationResult.getConfidenceScore(), retryCount + 1);
                        stateMachine.getExtendedState().getVariables()
                            .put("retryCount", retryCount + 1);
                        stateMachine.sendEvent(WorkflowEvent.NEEDS_RETRIEVAL);
                        return;
                    }
                }
                
                stateMachine.sendEvent(WorkflowEvent.VALIDATION_COMPLETE);
                log.info("Validation complete: confidence={}, valid={}", 
                    validationResult.getConfidenceScore(), validationResult.isValid());
            })
            .onErrorResume(error -> {
                log.warn("Validation failed after retries, using fallback", error);
                ValidationResult fallbackResult = fallbackStrategy.validationFallback(
                    synthesisResponse.response(), error);
                state.setValidationResult(fallbackResult);
                state.addMetadata("validationFallback", true);
                stateMachine.sendEvent(WorkflowEvent.VALIDATION_COMPLETE);
                return Mono.just(fallbackResult);
            });
    }
    
    /**
     * Build the final query response.
     */
    private Mono<QueryResponse> buildFinalResponse(
            StateMachine<WorkflowStatus, WorkflowEvent> stateMachine,
            AgentState state,
            AgentContext context,
            ValidationResult validationResult) {
        
        state.complete();
        
        // Convert documents to sources
        List<Source> sources = state.getRetrievedDocuments().stream()
            .map(doc -> new Source(
                doc.getId(),
                (String) doc.getMetadata().getOrDefault("title", "Unknown"),
                doc.getContent().substring(0, Math.min(200, doc.getContent().length())),
                1.0 // Default relevance score
            ))
            .collect(Collectors.toList());
        
        // Build metadata
        Duration duration = Duration.between(state.getStartTime(), state.getEndTime());
        Map<String, Object> additionalInfo = new HashMap<>();
        additionalInfo.put("executionId", context.getExecutionId());
        additionalInfo.put("sessionId", context.getSessionId());
        additionalInfo.put("retrievedDocumentCount", state.getRetrievedDocuments().size());
        additionalInfo.put("confidenceScore", validationResult.getConfidenceScore());
        additionalInfo.put("requiresHumanReview", validationResult.isRequiresHumanReview());
        
        ResponseMetadata metadata = new ResponseMetadata(
            context.getSharedData("tokensUsed", Integer.class).orElse(0),
            (int) duration.toMillis(),
            context.getSharedData("modelUsed", String.class).orElse("unknown"),
            Instant.now(),
            additionalInfo
        );
        
        QueryResponse response = new QueryResponse(
            state.getGeneratedResponse(),
            sources,
            validationResult.getConfidenceScore(),
            metadata
        );
        
        return Mono.just(response);
    }
    

    /**
     * Route a query to the appropriate workflow based on query type.
     * This allows for different workflows for different query types.
     */
    public Mono<QueryResponse> routeQuery(QueryRequest request) {
        // For now, all queries use the same workflow
        // In the future, we can route based on query classification
        return executeWorkflow(request);
    }
}
