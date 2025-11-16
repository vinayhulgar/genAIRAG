package com.support.assistant.controller;

import com.support.assistant.model.dto.QueryRequest;
import com.support.assistant.model.dto.QueryResponse;
import com.support.assistant.service.QueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * REST controller for handling query requests.
 * Provides endpoints for basic RAG queries and streaming responses.
 */
@RestController
@RequestMapping("/api/v1/query")
@RequiredArgsConstructor
@Slf4j
public class QueryController {

    private final QueryService queryService;

    /**
     * Processes a query through the RAG pipeline.
     * 
     * @param request the query request with user question
     * @return query response with generated answer and sources
     */
    @PostMapping
    public Mono<QueryResponse> query(@Valid @RequestBody QueryRequest request) {
        log.info("Received query request: {}", request.query());
        
        return Mono.fromCallable(() -> queryService.processQuery(request))
            .doOnSuccess(response -> log.info("Query processed successfully"))
            .doOnError(error -> log.error("Error processing query", error));
    }

    /**
     * Processes a query with streaming response.
     * Implementation will be added in Phase 4 (async agent execution).
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> queryStream(@Valid @RequestBody QueryRequest request) {
        log.info("Received streaming query request: {}", request.query());
        // Streaming implementation will be added in later tasks
        return Flux.just("Streaming response placeholder");
    }
}
