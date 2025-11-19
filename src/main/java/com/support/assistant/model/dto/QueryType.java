package com.support.assistant.model.dto;

/**
 * Enum representing different types of queries.
 * Used by QueryClassifier to categorize user queries.
 */
public enum QueryType {
    /**
     * Factual queries asking for specific information or facts.
     * Example: "What is the return policy?"
     */
    FACTUAL,
    
    /**
     * Comparison queries asking to compare two or more items.
     * Example: "What's the difference between Plan A and Plan B?"
     */
    COMPARISON,
    
    /**
     * Procedural queries asking how to do something.
     * Example: "How do I reset my password?"
     */
    PROCEDURAL,
    
    /**
     * Analytical queries requiring analysis or reasoning.
     * Example: "Why did my order fail?"
     */
    ANALYTICAL
}
