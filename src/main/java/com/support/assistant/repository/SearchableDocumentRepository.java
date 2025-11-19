package com.support.assistant.repository;

import com.support.assistant.model.entity.SearchableDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for Elasticsearch-based keyword search operations.
 * Provides BM25-based full-text search capabilities.
 */
@Repository
public interface SearchableDocumentRepository extends ElasticsearchRepository<SearchableDocument, String> {
}
