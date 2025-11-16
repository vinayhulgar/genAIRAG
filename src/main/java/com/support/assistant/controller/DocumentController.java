package com.support.assistant.controller;

import com.support.assistant.model.dto.DocumentUpload;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * REST controller for document management
 */
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Slf4j
public class DocumentController {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<String> uploadDocument(@Valid @RequestBody DocumentUpload upload) {
        log.info("Received document upload: {}", upload.filename());
        // Implementation will be added in later tasks
        return Mono.just("Document uploaded successfully");
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
