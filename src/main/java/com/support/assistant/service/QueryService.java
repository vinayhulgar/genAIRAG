package com.support.assistant.service;

import com.support.assistant.model.dto.QueryRequest;
import com.support.assistant.model.dto.QueryResponse;
import com.support.assistant.model.dto.ResponseMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for processing user queries using RAG pipeline.
 * Orchestrates retrieval and synthesis to generate responses.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QueryService {

    private final RetrievalService retrievalService;
    private final HybridSearchService hybridSearchService;
    private final MultiHopRetriever multiHopRetriever;
    private final SynthesisService synthesisService;

    @Value("${query.use-hybrid-search:true}")
    private boolean useHybridSearch;

    @Value("${multihop.enabled:true}")
    private boolean useMultiHop;

    /**
     * Processes a query through the RAG pipeline.
     * 
     * @param request the query request
     * @return query response with generated answer and sources
     */
    public QueryResponse processQuery(QueryRequest request) {
        log.info("Processing query: '{}'", request.query());
        long startTime = System.currentTimeMillis();
        
        try {
            // Step 1: Retrieve relevant documents using multi-hop, hybrid search, or vector search
            Map<String, Object> filters = extractFilters(request.context());
            List<Document> retrievedDocuments;
            String retrievalMethod;
            int hopsPerformed = 1;
            
            if (useMultiHop) {
                log.debug("Using multi-hop retrieval");
                MultiHopRetriever.MultiHopResult multiHopResult = multiHopRetriever.retrieve(
                    request.query(),
                    filters
                );
                retrievedDocuments = multiHopResult.documents();
                hopsPerformed = multiHopResult.hopsPerformed();
                retrievalMethod = "multi_hop_retrieval";
                log.debug("Multi-hop retrieval completed: {} documents across {} hops", 
                    retrievedDocuments.size(), hopsPerformed);
            } else if (useHybridSearch) {
                log.debug("Using hybrid search (vector + keyword + reranking)");
                retrievedDocuments = hybridSearchService.hybridSearch(
                    request.query(),
                    10, // top-k
                    filters
                );
                retrievalMethod = "hybrid_search";
            } else {
                log.debug("Using vector search only");
                retrievedDocuments = retrievalService.retrieve(
                    request.query(),
                    10, // top-k
                    filters
                );
                retrievalMethod = "vector_similarity";
            }
            
            log.debug("Retrieved {} documents using {}", retrievedDocuments.size(), retrievalMethod);
            
            // Step 2: Synthesize response using LLM
            SynthesisService.SynthesisResult synthesisResult = synthesisService.synthesize(
                request.query(),
                retrievedDocuments
            );
            
            // Step 3: Build response with metadata
            long latencyMs = System.currentTimeMillis() - startTime;
            
            ResponseMetadata metadata = new ResponseMetadata(
                synthesisResult.tokensUsed(),
                (int) latencyMs,
                synthesisResult.modelUsed(),
                Instant.now(),
                buildAdditionalInfo(retrievedDocuments.size(), retrievalMethod, hopsPerformed)
            );
            
            // For now, confidence score is set to 1.0 (will be implemented in validation phase)
            double confidenceScore = 1.0;
            
            log.info("Query processed successfully in {}ms", latencyMs);
            
            return new QueryResponse(
                synthesisResult.response(),
                synthesisResult.sources(),
                confidenceScore,
                metadata
            );
            
        } catch (Exception e) {
            log.error("Error processing query", e);
            throw new RuntimeException("Failed to process query", e);
        }
    }

    /**
     * Extracts metadata filters from request context.
     */
    private Map<String, Object> extractFilters(Map<String, Object> context) {
        if (context == null || context.isEmpty()) {
            return null;
        }
        
        Map<String, Object> filters = new HashMap<>();
        
        // Extract common filter fields
        if (context.containsKey("documentType")) {
            filters.put("documentType", context.get("documentType"));
        }
        if (context.containsKey("source")) {
            filters.put("source", context.get("source"));
        }
        if (context.containsKey("dateFrom")) {
            filters.put("dateFrom", context.get("dateFrom"));
        }
        if (context.containsKey("dateTo")) {
            filters.put("dateTo", context.get("dateTo"));
        }
        
        return filters.isEmpty() ? null : filters;
    }

    /**
     * Builds additional metadata information.
     */
    private Map<String, Object> buildAdditionalInfo(int documentsRetrieved, String retrievalMethod) {
        Map<String, Object> info = new HashMap<>();
        info.put("documentsRetrieved", documentsRetrieved);
        info.put("retrievalMethod", retrievalMethod);
        return info;
    }

    /**
     * Builds additional metadata information with hop count.
     */
    private Map<String, Object> buildAdditionalInfo(int documentsRetrieved, String retrievalMethod, int hopsPerformed) {
        Map<String, Object> info = buildAdditionalInfo(documentsRetrieved, retrievalMethod);
        if (hopsPerformed > 1) {
            info.put("hopsPerformed", hopsPerformed);
        }
        return info;
    }
}
