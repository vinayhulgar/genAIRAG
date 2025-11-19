package com.support.assistant.agent;

/**
 * Exception thrown when an agent fails to execute.
 */
public class AgentExecutionException extends RuntimeException {
    
    public AgentExecutionException(String message) {
        super(message);
    }
    
    public AgentExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
