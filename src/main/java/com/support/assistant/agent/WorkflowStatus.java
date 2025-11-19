package com.support.assistant.agent;

/**
 * Represents the current status of the agent workflow.
 */
public enum WorkflowStatus {
    /**
     * Workflow has been initialized but not started
     */
    INITIALIZED,
    
    /**
     * Query is being analyzed and decomposed
     */
    PLANNING,
    
    /**
     * Documents are being retrieved from vector store
     */
    RETRIEVING,
    
    /**
     * Context is being compressed
     */
    COMPRESSING,
    
    /**
     * Response is being generated
     */
    GENERATING,
    
    /**
     * Response is being validated for hallucinations
     */
    VALIDATING,
    
    /**
     * Workflow completed successfully
     */
    COMPLETE,
    
    /**
     * Workflow failed with an error
     */
    FAILED,
    
    /**
     * Workflow is waiting for retry
     */
    RETRYING
}
