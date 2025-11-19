package com.support.assistant.agent;

/**
 * Events that trigger state transitions in the agent workflow state machine.
 */
public enum WorkflowEvent {
    /**
     * Start the workflow
     */
    START,
    
    /**
     * Planning completed successfully
     */
    PLANNING_COMPLETE,
    
    /**
     * Retrieval completed successfully
     */
    RETRIEVAL_COMPLETE,
    
    /**
     * Compression completed successfully
     */
    COMPRESSION_COMPLETE,
    
    /**
     * Generation completed successfully
     */
    GENERATION_COMPLETE,
    
    /**
     * Validation completed successfully with high confidence
     */
    VALIDATION_COMPLETE,
    
    /**
     * Validation indicates low confidence, need to retry retrieval
     */
    NEEDS_RETRIEVAL,
    
    /**
     * An error occurred, trigger retry
     */
    ERROR,
    
    /**
     * Retry the current operation
     */
    RETRY,
    
    /**
     * Skip the current operation and proceed
     */
    SKIP,
    
    /**
     * Workflow failed and cannot continue
     */
    FAIL
}
