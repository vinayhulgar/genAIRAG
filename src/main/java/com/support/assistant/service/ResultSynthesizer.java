package com.support.assistant.service;

import com.support.assistant.model.dto.Source;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service for synthesizing results from multiple sub-queries into a coherent final answer.
 * Uses LLM to combine sub-query responses while maintaining citations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResultSynthesizer {

    private final ChatClient chatClient;

    private static final String MULTI_QUERY_SYNTHESIS_PROMPT = """
        You are a customer support assistant. You have answered multiple related questions separately.
        Now combine these answers into a single, coherent response that addresses the original query.
        
        Original Query: {originalQuery}
        
        Sub-query Answers:
        {subQueryAnswers}
        
        Instructions:
        1. Combine the answers into a natural, flowing response
        2. Maintain all source citations from the sub-answers using [Source: document_title]
        3. Ensure the response directly addresses the original query
        4. Remove redundant information while keeping all important details
        5. Organize the information logically
        6. If any sub-query failed to answer, acknowledge the limitation
        
        Combined Answer:""";

    /**
     * Synthesizes multiple sub-query results into a single coherent response.
     * 
     * @param originalQuery the original user query
     * @param subQueryResults results from executing sub-queries
     * @return synthesized final response with combined citations
     */
    public FinalSynthesisResult synthesizeResults(String originalQuery, 
                                                  List<SubQueryExecutor.SubQueryResult> subQueryResults) {
        log.info("Synthesizing results from {} sub-queries", subQueryResults.size());
        
        if (subQueryResults.isEmpty()) {
            log.warn("No sub-query results to synthesize");
            return new FinalSynthesisResult(
                "I don't have enough information to answer this question.",
                List.of(),
                0
            );
        }
        
        // If only one sub-query, return its result directly
        if (subQueryResults.size() == 1) {
            SubQueryExecutor.SubQueryResult singleResult = subQueryResults.get(0);
            return new FinalSynthesisResult(
                singleResult.response(),
                singleResult.sources(),
                singleResult.tokensUsed()
            );
        }
        
        try {
            // Build sub-query answers text
            String subQueryAnswers = buildSubQueryAnswersText(subQueryResults);
            
            // Create prompt for synthesis
            PromptTemplate promptTemplate = new PromptTemplate(MULTI_QUERY_SYNTHESIS_PROMPT);
            Map<String, Object> promptParams = new HashMap<>();
            promptParams.put("originalQuery", originalQuery);
            promptParams.put("subQueryAnswers", subQueryAnswers);
            
            // Generate combined response using LLM
            long startTime = System.currentTimeMillis();
            String combinedResponse = chatClient.prompt()
                .user(promptTemplate.create(promptParams).getContents())
                .call()
                .content();
            long duration = System.currentTimeMillis() - startTime;
            
            log.info("Synthesized final response in {}ms", duration);
            
            // Combine all sources from sub-queries (deduplicated)
            List<Source> allSources = combineAndDeduplicateSources(subQueryResults);
            
            // Calculate total tokens used
            int totalTokens = subQueryResults.stream()
                .mapToInt(SubQueryExecutor.SubQueryResult::tokensUsed)
                .sum();
            
            // Add tokens for final synthesis (rough estimate)
            totalTokens += (subQueryAnswers.length() + combinedResponse.length()) / 4;
            
            return new FinalSynthesisResult(
                combinedResponse,
                allSources,
                totalTokens
            );
            
        } catch (Exception e) {
            log.error("Error synthesizing results: {}", e.getMessage(), e);
            
            // Fallback: concatenate sub-query responses
            return fallbackSynthesis(subQueryResults);
        }
    }

    /**
     * Builds formatted text of all sub-query answers.
     */
    private String buildSubQueryAnswersText(List<SubQueryExecutor.SubQueryResult> results) {
        StringBuilder builder = new StringBuilder();
        
        for (int i = 0; i < results.size(); i++) {
            SubQueryExecutor.SubQueryResult result = results.get(i);
            
            builder.append(String.format("%d. Question: %s\n", i + 1, result.query()));
            
            if (result.success()) {
                builder.append(String.format("   Answer: %s\n\n", result.response()));
            } else {
                builder.append("   Answer: [Failed to retrieve answer]\n\n");
            }
        }
        
        return builder.toString();
    }

    /**
     * Combines and deduplicates sources from all sub-query results.
     */
    private List<Source> combineAndDeduplicateSources(List<SubQueryExecutor.SubQueryResult> results) {
        // Use LinkedHashSet to maintain order and remove duplicates
        Set<String> seenDocumentIds = new LinkedHashSet<>();
        List<Source> combinedSources = new ArrayList<>();
        
        for (SubQueryExecutor.SubQueryResult result : results) {
            if (result.success() && result.sources() != null) {
                for (Source source : result.sources()) {
                    // Only add if we haven't seen this document ID before
                    if (seenDocumentIds.add(source.documentId())) {
                        combinedSources.add(source);
                    }
                }
            }
        }
        
        log.debug("Combined {} unique sources from {} sub-queries", 
            combinedSources.size(), results.size());
        
        return combinedSources;
    }

    /**
     * Fallback synthesis when LLM synthesis fails.
     * Simply concatenates sub-query responses.
     */
    private FinalSynthesisResult fallbackSynthesis(List<SubQueryExecutor.SubQueryResult> results) {
        log.warn("Using fallback synthesis (concatenation)");
        
        StringBuilder responseBuilder = new StringBuilder();
        
        for (int i = 0; i < results.size(); i++) {
            SubQueryExecutor.SubQueryResult result = results.get(i);
            
            if (i > 0) {
                responseBuilder.append("\n\n");
            }
            
            if (result.success()) {
                responseBuilder.append(result.response());
            } else {
                responseBuilder.append("Unable to answer: ").append(result.query());
            }
        }
        
        List<Source> allSources = combineAndDeduplicateSources(results);
        
        int totalTokens = results.stream()
            .mapToInt(SubQueryExecutor.SubQueryResult::tokensUsed)
            .sum();
        
        return new FinalSynthesisResult(
            responseBuilder.toString(),
            allSources,
            totalTokens
        );
    }

    /**
     * Result of final synthesis combining multiple sub-query results.
     */
    public record FinalSynthesisResult(
        String response,
        List<Source> sources,
        int tokensUsed
    ) {}
}
