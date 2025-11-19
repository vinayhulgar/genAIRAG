package com.support.assistant.config;

import com.support.assistant.agent.AgentExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for retry logic across the application.
 * Enables Spring Retry and configures retry templates for different scenarios.
 */
@Configuration
@EnableRetry
@Slf4j
public class RetryConfig {
    
    /**
     * Default retry template with exponential backoff.
     * Used for general retryable operations.
     */
    @Bean
    public RetryTemplate retryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();
        
        // Configure retry policy - max 3 attempts
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(3);
        retryTemplate.setRetryPolicy(retryPolicy);
        
        // Configure backoff policy - exponential backoff
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(1000); // 1 second
        backOffPolicy.setMultiplier(2.0); // Double each time
        backOffPolicy.setMaxInterval(10000); // Max 10 seconds
        retryTemplate.setBackOffPolicy(backOffPolicy);
        
        // Add retry listener for logging
        retryTemplate.registerListener(new RetryLoggingListener());
        
        return retryTemplate;
    }
    
    /**
     * Retry template for agent operations with custom retry policy.
     * Retries on specific exceptions only.
     */
    @Bean
    public RetryTemplate agentRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();
        
        // Configure retry policy for specific exceptions
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
        retryableExceptions.put(AgentExecutionException.class, true);
        retryableExceptions.put(RuntimeException.class, true);
        
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(3, retryableExceptions);
        retryTemplate.setRetryPolicy(retryPolicy);
        
        // Configure backoff policy
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(500); // 500ms
        backOffPolicy.setMultiplier(2.0);
        backOffPolicy.setMaxInterval(5000); // Max 5 seconds
        retryTemplate.setBackOffPolicy(backOffPolicy);
        
        retryTemplate.registerListener(new RetryLoggingListener());
        
        return retryTemplate;
    }
    
    /**
     * Retry listener for logging retry attempts.
     */
    private static class RetryLoggingListener implements RetryListener {
        
        @Override
        public <T, E extends Throwable> void onError(
                RetryContext context, 
                RetryCallback<T, E> callback, 
                Throwable throwable) {
            log.warn("Retry attempt {} failed: {}", 
                context.getRetryCount(), 
                throwable.getMessage());
        }
        
        @Override
        public <T, E extends Throwable> void close(
                RetryContext context, 
                RetryCallback<T, E> callback, 
                Throwable throwable) {
            if (throwable != null) {
                log.error("All retry attempts exhausted. Final error: {}", 
                    throwable.getMessage());
            } else {
                if (context.getRetryCount() > 0) {
                    log.info("Operation succeeded after {} retry attempts", 
                        context.getRetryCount());
                }
            }
        }
    }
}
