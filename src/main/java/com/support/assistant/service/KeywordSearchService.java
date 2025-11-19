package com.support.assistant.service;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import com.support.assistant.model.entity.SearchableDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for keyword-based search using Elasticsearch BM25 algorithm.
 * Provides full-text search capabilities with inverted indexes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KeywordSearchService {

    private final ElasticsearchOperations elasticsearchOperations;

    /**
     * Performs BM25-based keyword search on document content.
     * 
     * @param query the search query
     * @param topK number of top results to return
     * @return list of search results with BM25 scores
     */
    public List<SearchResult> search(String query, int topK) {
        log.debug("Performing keyword search for query: '{}' with topK={}", query, topK);
        
        if (topK <= 0) {
            topK = 10;
        }

        try {
            // Build BM25 query using match query on content field
            Query matchQuery = QueryBuilders.match()
                .field("content")
                .query(query)
                .build()
                ._toQuery();

            NativeQuery searchQuery = NativeQuery.builder()
                .withQuery(matchQuery)
                .withPageable(PageRequest.of(0, topK))
                .build();

            SearchHits<SearchableDocument> searchHits = 
                elasticsearchOperations.search(searchQuery, SearchableDocument.class);

            List<SearchResult> results = searchHits.getSearchHits().stream()
                .map(hit -> new SearchResult(
                    hit.getContent().getId(),
                    hit.getContent().getContent(),
                    hit.getContent().getTitle(),
                    hit.getContent().getSource(),
                    hit.getScore()
                ))
                .collect(Collectors.toList());

            log.info("Keyword search returned {} results", results.size());
            return results;

        } catch (Exception e) {
            log.error("Error during keyword search", e);
            throw new RuntimeException("Failed to perform keyword search", e);
        }
    }

    /**
     * Represents a keyword search result with BM25 score.
     */
    public record SearchResult(
        String id,
        String content,
        String title,
        String source,
        float score
    ) {}
}
