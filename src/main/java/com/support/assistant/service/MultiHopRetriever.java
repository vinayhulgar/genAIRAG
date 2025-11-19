package com.support.assistant.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for performing multi-hop retrieval across the knowledge base.
 * Performs 2-3 rounds of retrieval using extracted entities for follow-up queries.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MultiHopRetriever {

    private final RetrievalService retrievalService;
    private final EntityExtractor entityExtractor;

    @Value("${multihop.max-hops:3}")
    private int maxHops;

    @Value("${multihop.top-k-per-hop:10}")
    private int topKPerHop;

    @Value("${multihop.enabled:true}")
    private boolean enabled;

    /**
     * Performs multi-hop retrieval for complex queries.
     * 
     * @param query the original user query
     * @param filters optional metadata filters
     * @return combined and deduplicated results from all hops
     */
    public MultiHopResult retrieve(String query, Map<String, Object> filters) {
        if (!enabled) {
            log.debug("Multi-hop retrieval disabled, performing single retrieval");
            List<Document> documents = retrievalService.retrieve(query, topKPerHop, filters);
            return new MultiHopResult(documents, 1, List.of());
        }

        log.info("Starting multi-hop retrieval for query: '{}'", query);
        
        // Track all retrieved documents and their IDs for deduplication
        Map<String, Document> allDocuments = new LinkedHashMap<>();
        List<String> hopQueries = new ArrayList<>();
        hopQueries.add(query);
        
        try {
            // Hop 1: Initial retrieval with original query
            log.debug("Hop 1: Retrieving with original query");
            List<Document> hop1Documents = retrievalService.retrieve(query, topKPerHop, filters);
            addDocuments(allDocuments, hop1Documents);
            log.info("Hop 1: Retrieved {} documents ({} unique)", 
                hop1Documents.size(), allDocuments.size());
            
            // Extract entities from first hop results
            List<String> entities = entityExtractor.extractEntities(query, hop1Documents);
            
            if (entities.isEmpty()) {
                log.info("No entities extracted, stopping at 1 hop");
                return new MultiHopResult(
                    new ArrayList<>(allDocuments.values()), 
                    1, 
                    hopQueries
                );
            }
            
            // Hop 2: Retrieve using extracted entities
            int currentHop = 2;
            for (String entity : entities) {
                if (currentHop > maxHops) {
                    log.debug("Reached max hops ({}), stopping", maxHops);
                    break;
                }
                
                log.debug("Hop {}: Retrieving with entity: '{}'", currentHop, entity);
                String entityQuery = buildEntityQuery(query, entity);
                hopQueries.add(entityQuery);
                
                List<Document> hopDocuments = retrievalService.retrieve(
                    entityQuery, 
                    topKPerHop, 
                    filters
                );
                
                int beforeSize = allDocuments.size();
                addDocuments(allDocuments, hopDocuments);
                int newDocs = allDocuments.size() - beforeSize;
                
                log.info("Hop {}: Retrieved {} documents ({} new, {} total unique)", 
                    currentHop, hopDocuments.size(), newDocs, allDocuments.size());
                
                currentHop++;
            }
            
            List<Document> finalDocuments = new ArrayList<>(allDocuments.values());
            log.info("Multi-hop retrieval complete: {} total unique documents across {} hops", 
                finalDocuments.size(), currentHop - 1);
            
            return new MultiHopResult(finalDocuments, currentHop - 1, hopQueries);
            
        } catch (Exception e) {
            log.error("Error during multi-hop retrieval, returning documents from completed hops", e);
            return new MultiHopResult(
                new ArrayList<>(allDocuments.values()), 
                hopQueries.size(), 
                hopQueries
            );
        }
    }

    /**
     * Convenience method for multi-hop retrieval without filters.
     */
    public MultiHopResult retrieve(String query) {
        return retrieve(query, null);
    }

    /**
     * Adds documents to the collection, deduplicating by document ID.
     */
    private void addDocuments(Map<String, Document> allDocuments, List<Document> newDocuments) {
        for (Document doc : newDocuments) {
            String docId = extractDocumentId(doc);
            
            // Only add if not already present (deduplication)
            if (!allDocuments.containsKey(docId)) {
                allDocuments.put(docId, doc);
            }
        }
    }

    /**
     * Extracts a unique identifier for a document.
     * Uses metadata ID if available, otherwise uses content hash.
     */
    private String extractDocumentId(Document doc) {
        // Try to get ID from metadata
        Object idObj = doc.getMetadata().get("id");
        if (idObj != null) {
            return idObj.toString();
        }
        
        // Fallback: use content hash for deduplication
        return String.valueOf(doc.getContent().hashCode());
    }

    /**
     * Builds a query for entity-based retrieval.
     * Combines original query context with entity focus.
     */
    private String buildEntityQuery(String originalQuery, String entity) {
        // Create a focused query that combines the original intent with the entity
        return String.format("%s %s", entity, originalQuery);
    }

    /**
     * Result of multi-hop retrieval operation.
     */
    public record MultiHopResult(
        List<Document> documents,
        int hopsPerformed,
        List<String> hopQueries
    ) {}
}
