package com.support.assistant.service;

import com.support.assistant.model.dto.Source;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for generating responses using LLM with retrieved context.
 * Implements RAG synthesis with prompt engineering and source citation.
 * Supports reactive async execution with Mono.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SynthesisService {

    private final ChatClient chatClient;

    private static final String SYNTHESIS_PROMPT_TEMPLATE = """
        You are a customer support assistant. Answer the question based ONLY on the provided context.
        
        Context:
        {context}
        
        Question: {query}
        
        Instructions:
        1. Answer directly and concisely
        2. Cite sources using [Source: document_title]
        3. If the context doesn't contain the answer, say "I don't have enough information to answer this question"
        4. Do not make up information
        
        Answer:""";

    /**
     * Generates a response using the LLM with retrieved context reactively.
     * 
     * @param query the user's question
     * @param retrievedDocuments documents retrieved from vector search
     * @return Mono of synthesized response with citations
     */
    public Mono<SynthesisResult> synthesizeAsync(String query, List<Document> retrievedDocuments) {
        log.debug("Synthesizing response asynchronously for query: '{}'", query);
        
        return Mono.fromCallable(() -> synthesize(query, retrievedDocuments))
            .subscribeOn(Schedulers.boundedElastic())
            .doOnSuccess(result -> log.debug("Async synthesis completed: {} tokens", result.tokensUsed()))
            .doOnError(error -> log.error("Async synthesis failed", error));
    }

    /**
     * Generates a response using the LLM with retrieved context (synchronous).
     * 
     * @param query the user's question
     * @param retrievedDocuments documents retrieved from vector search
     * @return synthesized response with citations
     */
    public SynthesisResult synthesize(String query, List<Document> retrievedDocuments) {
        log.debug("Synthesizing response for query: '{}'", query);
        
        if (retrievedDocuments == null || retrievedDocuments.isEmpty()) {
            log.warn("No documents provided for synthesis");
            return new SynthesisResult(
                "I don't have enough information to answer this question.",
                List.of(),
                0,
                "none"
            );
        }

        try {
            // Build context from retrieved documents
            String context = buildContext(retrievedDocuments);
            
            // Create prompt with context injection
            PromptTemplate promptTemplate = new PromptTemplate(SYNTHESIS_PROMPT_TEMPLATE);
            Map<String, Object> promptParams = new HashMap<>();
            promptParams.put("context", context);
            promptParams.put("query", query);
            
            // Generate response using ChatClient
            long startTime = System.currentTimeMillis();
            String response = chatClient.prompt()
                .user(promptTemplate.create(promptParams).getContents())
                .call()
                .content();
            long duration = System.currentTimeMillis() - startTime;
            
            log.info("Generated response in {}ms", duration);
            
            // Extract citations from response
            List<Source> sources = extractSources(response, retrievedDocuments);
            
            // Estimate tokens used (rough approximation: 1 token ≈ 4 characters)
            int tokensUsed = (context.length() + query.length() + response.length()) / 4;
            
            return new SynthesisResult(
                response,
                sources,
                tokensUsed,
                "llm" // Will be updated with actual model name in future
            );
            
        } catch (Exception e) {
            log.error("Error during response synthesis", e);
            throw new RuntimeException("Failed to generate response", e);
        }
    }

    /**
     * Builds context string from retrieved documents.
     */
    private String buildContext(List<Document> documents) {
        StringBuilder contextBuilder = new StringBuilder();
        
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            String title = doc.getMetadata().getOrDefault("title", "Document " + (i + 1)).toString();
            String content = doc.getContent();
            
            contextBuilder.append("--- Document: ").append(title).append(" ---\n");
            contextBuilder.append(content).append("\n\n");
        }
        
        return contextBuilder.toString();
    }

    /**
     * Extracts source citations from the generated response.
     * Looks for patterns like [Source: document_title]
     */
    private List<Source> extractSources(String response, List<Document> retrievedDocuments) {
        List<Source> sources = new ArrayList<>();
        Pattern pattern = Pattern.compile("\\[Source:\\s*([^\\]]+)\\]");
        Matcher matcher = pattern.matcher(response);
        
        while (matcher.find()) {
            String citedTitle = matcher.group(1).trim();
            
            // Find matching document
            for (Document doc : retrievedDocuments) {
                String docTitle = doc.getMetadata().getOrDefault("title", "").toString();
                if (docTitle.equalsIgnoreCase(citedTitle) || docTitle.contains(citedTitle)) {
                    String documentId = doc.getMetadata().getOrDefault("id", "unknown").toString();
                    String excerpt = doc.getContent().substring(0, Math.min(200, doc.getContent().length()));
                    
                    sources.add(new Source(
                        documentId,
                        docTitle,
                        excerpt + "...",
                        1.0 // Will be replaced with actual similarity score in future
                    ));
                    break;
                }
            }
        }
        
        // If no explicit citations found, include all retrieved documents as sources
        if (sources.isEmpty()) {
            for (Document doc : retrievedDocuments) {
                String documentId = doc.getMetadata().getOrDefault("id", "unknown").toString();
                String title = doc.getMetadata().getOrDefault("title", "Untitled").toString();
                String excerpt = doc.getContent().substring(0, Math.min(200, doc.getContent().length()));
                
                sources.add(new Source(
                    documentId,
                    title,
                    excerpt + "...",
                    1.0
                ));
            }
        }
        
        return sources;
    }

    /**
     * Result of synthesis operation.
     */
    public record SynthesisResult(
        String response,
        List<Source> sources,
        int tokensUsed,
        String modelUsed
    ) {}
}
