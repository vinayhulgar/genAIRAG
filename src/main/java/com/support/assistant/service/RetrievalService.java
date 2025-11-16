package com.support.assistant.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service for retrieving relevant documents using vector similarity search.
 * Implements top-k retrieval with metadata filtering capabilities.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RetrievalService {

    private final VectorStore vectorStore;

    /**
     * Performs vector similarity search to retrieve relevant documents.
     * 
     * @param query the search query
     * @param topK number of top results to return (default: 10)
     * @param filters optional metadata filters (document type, date range, etc.)
     * @return list of relevant documents with similarity scores
     */
    public List<Document> retrieve(String query, int topK, Map<String, Object> filters) {
        log.debug("Performing vector search for query: '{}' with topK={}", query, topK);
        
        if (topK <= 0) {
            topK = 10; // default value
        }
        
        try {
            SearchRequest searchRequest;
            
            // Apply metadata filters if provided
            if (filters != null && !filters.isEmpty()) {
                Filter.Expression filterExpression = buildFilterExpression(filters);
                if (filterExpression != null) {
                    searchRequest = SearchRequest.query(query)
                        .withTopK(topK)
                        .withFilterExpression(filterExpression);
                    log.debug("Applied filters: {}", filters);
                } else {
                    searchRequest = SearchRequest.query(query).withTopK(topK);
                }
            } else {
                searchRequest = SearchRequest.query(query).withTopK(topK);
            }
            
            List<Document> results = vectorStore.similaritySearch(searchRequest);
            
            log.info("Retrieved {} documents for query", results.size());
            return results;
            
        } catch (Exception e) {
            log.error("Error during vector similarity search", e);
            throw new RuntimeException("Failed to retrieve documents", e);
        }
    }

    /**
     * Convenience method for retrieval with default topK=10 and no filters.
     */
    public List<Document> retrieve(String query) {
        return retrieve(query, 10, null);
    }

    /**
     * Builds filter expression from metadata filters.
     * Supports filtering by document type, date range, and custom metadata.
     * Note: Currently supports single filter. Multiple filter support will be added in future phases.
     */
    private Filter.Expression buildFilterExpression(Map<String, Object> filters) {
        try {
            FilterExpressionBuilder builder = new FilterExpressionBuilder();
            
            // For now, apply the first available filter
            // Multiple filter combination will be enhanced in later phases
            for (Map.Entry<String, Object> entry : filters.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                
                switch (key) {
                    case "documentType":
                        return builder.eq("document_type", value).build();
                    case "source":
                        return builder.eq("source", value).build();
                    case "dateFrom":
                        if (value instanceof Instant) {
                            return builder.gte("created_at", value).build();
                        }
                        break;
                    case "dateTo":
                        if (value instanceof Instant) {
                            return builder.lte("created_at", value).build();
                        }
                        break;
                    default:
                        // Support custom metadata fields
                        return builder.eq(key, value).build();
                }
            }
            
            return null;
        } catch (Exception e) {
            log.warn("Failed to build filter expression, proceeding without filters", e);
            return null;
        }
    }
}
