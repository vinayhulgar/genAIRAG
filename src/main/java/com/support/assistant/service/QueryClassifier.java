package com.support.assistant.service;

import com.support.assistant.model.dto.QueryClassification;
import com.support.assistant.model.dto.QueryType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Service for classifying user queries into different types.
 * Uses LLM with structured output to categorize queries.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QueryClassifier {

    private final ChatClient chatClient;

    private static final String CLASSIFICATION_PROMPT = """
        You are a query classification expert. Analyze the following user query and classify it into one of these types:
        
        1. FACTUAL - Queries asking for specific information or facts
           Examples: "What is the return policy?", "What are the business hours?"
        
        2. COMPARISON - Queries asking to compare two or more items
           Examples: "What's the difference between Plan A and Plan B?", "Compare product X and Y"
        
        3. PROCEDURAL - Queries asking how to do something or step-by-step instructions
           Examples: "How do I reset my password?", "How to install the software?"
        
        4. ANALYTICAL - Queries requiring analysis, reasoning, or explanation of why something happened
           Examples: "Why did my order fail?", "What caused the error?"
        
        User Query: {query}
        
        Classify this query and provide your reasoning.
        
        {format}
        """;

    /**
     * Classifies a user query into a specific query type.
     *
     * @param query the user query to classify
     * @return QueryClassification with type, confidence, and reasoning
     */
    public QueryClassification classify(String query) {
        log.debug("Classifying query: '{}'", query);
        
        try {
            // Create output converter for structured response
            BeanOutputConverter<ClassificationResult> outputConverter = 
                new BeanOutputConverter<>(ClassificationResult.class);
            
            // Build prompt with format instructions
            PromptTemplate promptTemplate = new PromptTemplate(CLASSIFICATION_PROMPT);
            String prompt = promptTemplate.create(Map.of(
                "query", query,
                "format", outputConverter.getFormat()
            )).getContents();
            
            // Call LLM with structured output
            String response = chatClient.prompt()
                .user(prompt)
                .call()
                .content();
            
            // Parse structured response
            ClassificationResult result = outputConverter.convert(response);
            
            QueryType queryType = QueryType.valueOf(result.queryType().toUpperCase());
            
            log.debug("Query classified as {} with confidence {}", queryType, result.confidence());
            
            return new QueryClassification(
                queryType,
                result.confidence(),
                result.reasoning()
            );
            
        } catch (Exception e) {
            log.warn("Error classifying query, defaulting to FACTUAL: {}", e.getMessage());
            // Default to FACTUAL if classification fails
            return new QueryClassification(
                QueryType.FACTUAL,
                0.5,
                "Classification failed, defaulted to FACTUAL"
            );
        }
    }

    /**
     * Internal record for LLM structured output parsing.
     */
    public record ClassificationResult(
        String queryType,
        double confidence,
        String reasoning
    ) {}
}
