package com.support.assistant.agent;

import reactor.core.publisher.Mono;

/**
 * Base interface for all agents in the multi-agent orchestration system.
 * Each agent is responsible for a specific task in the RAG pipeline.
 *
 * @param <I> Input type for the agent
 * @param <O> Output type for the agent
 */
public interface Agent<I, O> {
    
    /**
     * Execute the agent's task with the given input.
     *
     * @param input The input data for the agent
     * @param context The agent context containing shared state and configuration
     * @return A Mono containing the agent's output
     */
    Mono<O> execute(I input, AgentContext context);
    
    /**
     * Get the name of this agent for logging and monitoring.
     *
     * @return The agent name
     */
    String getName();
    
    /**
     * Determine if this agent can handle the given input.
     * Used for conditional routing in the orchestrator.
     *
     * @param input The input to check
     * @param context The agent context
     * @return true if this agent can handle the input
     */
    default boolean canHandle(I input, AgentContext context) {
        return true;
    }
}
