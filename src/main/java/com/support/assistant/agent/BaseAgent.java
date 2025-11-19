package com.support.assistant.agent;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Abstract base class for agents providing common functionality.
 *
 * @param <I> Input type for the agent
 * @param <O> Output type for the agent
 */
@Slf4j
public abstract class BaseAgent<I, O> implements Agent<I, O> {
    
    private final String name;
    
    protected BaseAgent(String name) {
        this.name = name;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public Mono<O> execute(I input, AgentContext context) {
        log.info("Agent [{}] starting execution for executionId: {}", name, context.getExecutionId());
        
        return Mono.defer(() -> doExecute(input, context))
                .timeout(Duration.ofMillis(context.getTimeoutMs()))
                .doOnSuccess(output -> {
                    log.info("Agent [{}] completed successfully for executionId: {}", 
                            name, context.getExecutionId());
                    if (context.isDebug()) {
                        log.debug("Agent [{}] output: {}", name, output);
                    }
                })
                .doOnError(error -> {
                    log.error("Agent [{}] failed for executionId: {} with error: {}", 
                            name, context.getExecutionId(), error.getMessage(), error);
                })
                .onErrorResume(error -> handleError(input, context, error));
    }
    
    /**
     * Implement the actual agent logic in this method.
     *
     * @param input The input data
     * @param context The agent context
     * @return A Mono containing the output
     */
    protected abstract Mono<O> doExecute(I input, AgentContext context);
    
    /**
     * Handle errors that occur during execution.
     * Override this method to provide custom error handling.
     *
     * @param input The input data
     * @param context The agent context
     * @param error The error that occurred
     * @return A Mono that either recovers from the error or propagates it
     */
    protected Mono<O> handleError(I input, AgentContext context, Throwable error) {
        log.error("Agent [{}] error handler invoked", name);
        return Mono.error(new AgentExecutionException(
                String.format("Agent [%s] failed: %s", name, error.getMessage()),
                error
        ));
    }
    
    /**
     * Validate input before execution.
     * Override this method to add custom validation logic.
     *
     * @param input The input to validate
     * @param context The agent context
     * @throws IllegalArgumentException if validation fails
     */
    protected void validateInput(I input, AgentContext context) {
        if (input == null) {
            throw new IllegalArgumentException("Input cannot be null for agent: " + name);
        }
    }
    
    /**
     * Log debug information if debug mode is enabled
     */
    protected void debugLog(AgentContext context, String message, Object... args) {
        if (context.isDebug()) {
            log.debug("[{}] " + message, name, args);
        }
    }
}
