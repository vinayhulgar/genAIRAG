package com.support.assistant.service;

import com.support.assistant.model.entity.Document;
import com.support.assistant.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Service for document management operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final DocumentRepository documentRepository;

    public Mono<Document> saveDocument(Document document) {
        log.debug("Saving document: {}", document.getId());
        return Mono.fromCallable(() -> documentRepository.save(document));
    }

    public Mono<Document> getDocument(String id) {
        log.debug("Fetching document: {}", id);
        return Mono.fromCallable(() -> documentRepository.findById(id).orElse(null));
    }

    public Mono<Void> deleteDocument(String id) {
        log.debug("Deleting document: {}", id);
        return Mono.fromRunnable(() -> documentRepository.deleteById(id));
    }
}
