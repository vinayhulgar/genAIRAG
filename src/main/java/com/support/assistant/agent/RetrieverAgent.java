package com.support.assistant.agent;

import com.support.assistant.service.HybridSearchService;
import com.support.assistant.service.MultiHopRetriever;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Agent responsible for document retrieval using hybrid search and multi-hop retrieval.
 * Combines vector similarity search with keyword search and performs iterative retrieval.
 */
@Component
@Slf4j
public class RetrieverAgent extends BaseAgent<RetrievalRequest, RetrievalResponse> {
    
    private final HybridSearchService hybridSearchService;
    private final MultiHopRetriever multiHopRetriever;
    
    @Value("${retrieval.default-top-k:10}")
    private int defaultTopK;
    
    @Value("${retrieval.use-multi-hop:true}")
    private boolean useMultiHop;
    
    public RetrieverAgent(HybridSearchService hybridSearchService, 
                         MultiHopRetriever multiHopRetriever) {
        super("RetrieverAgent");
        this.hybridSearchService = hybridSearchService;
        this.multiHopRetriever = multiHopRetriever;
    }
    
    @Override
    protected Mono<RetrievalResponse> doExecute(RetrievalRequest request, AgentContext context) {
        validateInput(request, context);
        
        String query = request.query();
        int topK = request.topK() > 0 ? request.topK() : defaultTopK;
        Map<String, Object> filters = request.filters();
        boolean multiHop = request.useMultiHop() && useMultiHop;
        
        debugLog(context, "Retrieving documents for query: {} (topK={}, multiHop={})", 
                query, topK, multiHop);
        
        return Mono.fromCallable(() -> {
            List<Document> documents;
            int hopsPerformed = 1;
            
            if (multiHop) {
                // Use multi-hop retrieval for complex queries
                debugLog(context, "Performing multi-hop retrieval");
                MultiHopRetriever.MultiHopResult result = multiHopRetriever.retrieve(query, filters);
                documents = result.documents();
                hopsPerformed = result.hopsPerformed();
                
                // Limit to topK after multi-hop
                if (documents.size() > topK) {
                    documents = documents.subList(0, topK);
                }
                
                context.setSharedData("hopQueries", result.hopQueries());
            } else {
                // Use standard hybrid search
                debugLog(context, "Performing hybrid search");
                documents = hybridSearchService.hybridSearch(query, topK, filters);
            }
            
            log.info("Retrieved {} documents (hops: {})", documents.size(), hopsPerformed);
            
            // Store retrieval metadata in context
            context.setSharedData("retrievedDocumentCount", documents.size());
            context.setSharedData("hopsPerformed", hopsPerformed);
            
            return new RetrievalResponse(documents, hopsPerformed);
        });
    }
    
    @Override
    protected void validateInput(RetrievalRequest request, AgentContext context) {
        super.validateInput(request, context);
        if (request.query() == null || request.query().trim().isEmpty()) {
            throw new IllegalArgumentException("Query cannot be empty");
        }
    }
    
    @Override
    public boolean canHandle(RetrievalRequest request, AgentContext context) {
        return request != null && request.query() != null && !request.query().trim().isEmpty();
    }
}
