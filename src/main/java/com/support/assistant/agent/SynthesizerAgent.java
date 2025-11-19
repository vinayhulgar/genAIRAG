package com.support.assistant.agent;

import com.support.assistant.service.SynthesisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Agent responsible for generating responses using LLM with retrieved context.
 * Synthesizes information from multiple documents into a coherent answer.
 */
@Component
@Slf4j
public class SynthesizerAgent extends BaseAgent<SynthesisRequest, SynthesisResponse> {
    
    private final SynthesisService synthesisService;
    
    public SynthesizerAgent(SynthesisService synthesisService) {
        super("SynthesizerAgent");
        this.synthesisService = synthesisService;
    }
    
    @Override
    protected Mono<SynthesisResponse> doExecute(SynthesisRequest request, AgentContext context) {
        validateInput(request, context);
        
        String query = request.query();
        List<Document> documents = request.documents();
        
        debugLog(context, "Synthesizing response for query with {} documents", 
                documents.size());
        
        return Mono.fromCallable(() -> {
            // Use SynthesisService to generate response
            SynthesisService.SynthesisResult result = 
                synthesisService.synthesize(query, documents);
            
            log.info("Generated response with {} sources, {} tokens used", 
                    result.sources().size(), result.tokensUsed());
            
            // Store synthesis metadata in context
            context.setSharedData("tokensUsed", result.tokensUsed());
            context.setSharedData("modelUsed", result.modelUsed());
            context.setSharedData("sourceCount", result.sources().size());
            
            return new SynthesisResponse(
                result.response(),
                result.sources(),
                result.tokensUsed(),
                result.modelUsed()
            );
        });
    }
    
    @Override
    protected void validateInput(SynthesisRequest request, AgentContext context) {
        super.validateInput(request, context);
        if (request.query() == null || request.query().trim().isEmpty()) {
            throw new IllegalArgumentException("Query cannot be empty");
        }
        if (request.documents() == null || request.documents().isEmpty()) {
            throw new IllegalArgumentException("Documents cannot be empty for synthesis");
        }
    }
    
    @Override
    public boolean canHandle(SynthesisRequest request, AgentContext context) {
        return request != null 
            && request.query() != null 
            && !request.query().trim().isEmpty()
            && request.documents() != null 
            && !request.documents().isEmpty();
    }
}
