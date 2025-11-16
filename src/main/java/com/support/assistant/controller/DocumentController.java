package com.support.assistant.controller;

import com.support.assistant.model.dto.DocumentUpload;
import com.support.assistant.service.DocumentIngestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for document management
 */
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Slf4j
public class DocumentController {

    private final DocumentIngestionService documentIngestionService;

    /**
     * Uploads and ingests a document into the knowledge base.
     * Supports PDF, Markdown, and plain text formats.
     *
     * @param upload the document upload request
     * @return response with ingestion details
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Map<String, Object>> uploadDocument(@Valid @RequestBody DocumentUpload upload) {
        log.info("Received document upload: {} (type: {})", 
                upload.filename(), upload.documentType());
        
        return documentIngestionService.ingestDocument(upload)
                .map(chunkCount -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("message", "Document uploaded and ingested successfully");
                    response.put("filename", upload.filename());
                    response.put("chunksCreated", chunkCount);
                    response.put("status", "success");
                    return response;
                })
                .doOnSuccess(response -> 
                        log.info("Document '{}' uploaded successfully with {} chunks", 
                                upload.filename(), response.get("chunksCreated")))
                .doOnError(error -> 
                        log.error("Failed to upload document '{}': {}", 
                                upload.filename(), error.getMessage()));
    }

    @GetMapping("/{id}")
    public Mono<String> getDocument(@PathVariable String id) {
        log.info("Fetching document: {}", id);
        // Implementation will be added in later tasks
        return Mono.just("Document details placeholder");
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteDocument(@PathVariable String id) {
        log.info("Deleting document: {}", id);
        // Implementation will be added in later tasks
        return Mono.empty();
    }
}
