package com.support.assistant.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for extracting key entities and concepts from retrieved documents.
 * Used in multi-hop RAG to identify concepts that need additional context.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EntityExtractor {

    private final ChatClient chatClient;

    private static final String ENTITY_EXTRACTION_PROMPT = """
        Analyze the following documents and extract key entities and concepts that are mentioned but may need additional context or explanation.
        
        Documents:
        {documents}
        
        Original Query: {query}
        
        Instructions:
        1. Extract important entities (people, organizations, products, technical terms, concepts)
        2. Identify concepts that are mentioned but not fully explained
        3. Focus on entities relevant to answering the query
        4. Return ONLY a comma-separated list of entities, nothing else
        5. Limit to the 5 most important entities
        
        Entities:""";

    /**
     * Extracts key entities from retrieved documents that may need additional context.
     * 
     * @param query the original user query
     * @param retrievedDocuments documents retrieved in the first hop
     * @return list of extracted entities for follow-up retrieval
     */
    public List<String> extractEntities(String query, List<Document> retrievedDocuments) {
        log.debug("Extracting entities from {} documents for query: '{}'", 
            retrievedDocuments.size(), query);
        
        if (retrievedDocuments == null || retrievedDocuments.isEmpty()) {
            log.warn("No documents provided for entity extraction");
            return List.of();
        }

        try {
            // Build document context
            String documentsText = buildDocumentsText(retrievedDocuments);
            
            // Create prompt for entity extraction
            PromptTemplate promptTemplate = new PromptTemplate(ENTITY_EXTRACTION_PROMPT);
            Map<String, Object> promptParams = new HashMap<>();
            promptParams.put("documents", documentsText);
            promptParams.put("query", query);
            
            // Call LLM to extract entities
            String response = chatClient.prompt()
                .user(promptTemplate.create(promptParams).getContents())
                .call()
                .content();
            
            // Parse entities from response
            List<String> entities = parseEntities(response);
            
            log.info("Extracted {} entities: {}", entities.size(), entities);
            return entities;
            
        } catch (Exception e) {
            log.error("Error during entity extraction", e);
            // Return empty list on error to allow graceful degradation
            return List.of();
        }
    }

    /**
     * Builds a condensed text representation of documents for entity extraction.
     */
    private String buildDocumentsText(List<Document> documents) {
        StringBuilder builder = new StringBuilder();
        
        for (int i = 0; i < Math.min(documents.size(), 5); i++) {
            Document doc = documents.get(i);
            String title = doc.getMetadata().getOrDefault("title", "Document " + (i + 1)).toString();
            String content = doc.getContent();
            
            // Limit content length to avoid token overflow
            String truncatedContent = content.length() > 500 
                ? content.substring(0, 500) + "..." 
                : content;
            
            builder.append("Document ").append(i + 1).append(" (").append(title).append("):\n");
            builder.append(truncatedContent).append("\n\n");
        }
        
        return builder.toString();
    }

    /**
     * Parses entities from LLM response.
     * Expects comma-separated list of entities.
     */
    private List<String> parseEntities(String response) {
        if (response == null || response.trim().isEmpty()) {
            return List.of();
        }
        
        // Clean up response
        String cleaned = response.trim()
            .replaceAll("^Entities:\\s*", "")
            .replaceAll("^-\\s*", "")
            .trim();
        
        // Split by comma and clean each entity
        return Arrays.stream(cleaned.split(","))
            .map(String::trim)
            .filter(entity -> !entity.isEmpty())
            .filter(entity -> entity.length() > 2) // Filter out very short entities
            .limit(5) // Limit to 5 entities
            .collect(Collectors.toList());
    }

    /**
     * Result of entity extraction operation.
     */
    public record EntityExtractionResult(
        List<String> entities,
        String rawResponse
    ) {}
}
