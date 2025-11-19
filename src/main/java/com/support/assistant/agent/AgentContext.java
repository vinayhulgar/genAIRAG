package com.support.assistant.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Context object passed between agents containing shared configuration and data.
 * This allows agents to communicate and share information during workflow execution.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentContext {
    
    /**
     * Unique identifier for this workflow execution
     */
    private String executionId;
    
    /**
     * Session ID for tracking related queries
     */
    private String sessionId;
    
    /**
     * User ID if authentication is enabled
     */
    private String userId;
    
    /**
     * Configuration parameters for agents
     */
    @Builder.Default
    private Map<String, Object> configuration = new HashMap<>();
    
    /**
     * Shared data that agents can read and write
     */
    @Builder.Default
    private Map<String, Object> sharedData = new HashMap<>();
    
    /**
     * Maximum number of retry attempts for failed operations
     */
    @Builder.Default
    private int maxRetries = 3;
    
    /**
     * Timeout in milliseconds for agent execution
     */
    @Builder.Default
    private long timeoutMs = 30000;
    
    /**
     * Whether to enable debug logging
     */
    @Builder.Default
    private boolean debug = false;
    
    /**
     * Get a configuration value
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getConfig(String key, Class<T> type) {
        Object value = configuration.get(key);
        if (value != null && type.isInstance(value)) {
            return Optional.of((T) value);
        }
        return Optional.empty();
    }
    
    /**
     * Set a configuration value
     */
    public void setConfig(String key, Object value) {
        this.configuration.put(key, value);
    }
    
    /**
     * Get shared data
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getSharedData(String key, Class<T> type) {
        Object value = sharedData.get(key);
        if (value != null && type.isInstance(value)) {
            return Optional.of((T) value);
        }
        return Optional.empty();
    }
    
    /**
     * Set shared data
     */
    public void setSharedData(String key, Object value) {
        this.sharedData.put(key, value);
    }
    
    /**
     * Check if a configuration key exists
     */
    public boolean hasConfig(String key) {
        return configuration.containsKey(key);
    }
    
    /**
     * Check if a shared data key exists
     */
    public boolean hasSharedData(String key) {
        return sharedData.containsKey(key);
    }
    
    /**
     * Create a child context with the same configuration but fresh shared data
     */
    public AgentContext createChildContext() {
        return AgentContext.builder()
                .executionId(this.executionId)
                .sessionId(this.sessionId)
                .userId(this.userId)
                .configuration(new HashMap<>(this.configuration))
                .sharedData(new HashMap<>())
                .maxRetries(this.maxRetries)
                .timeoutMs(this.timeoutMs)
                .debug(this.debug)
                .build();
    }
}
