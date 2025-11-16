package com.support.assistant.controller;

import com.support.assistant.model.dto.QueryRequest;
import com.support.assistant.model.dto.QueryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * REST controller for handling query requests
 */
@RestController
@RequestMapping("/api/v1/query")
@RequiredArgsConstructor
@Slf4j
public class QueryController {

    @PostMapping
    public Mono<QueryResponse> query(@Valid @RequestBody QueryRequest request) {
        log.info("Received query request: {}", request.query());
        // Implementation will be added in later tasks
        return Mono.just(new QueryResponse(
            "Response placeholder",
            null,
            0.0,
            null
        ));
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> queryStream(@Valid @RequestBody QueryRequest request) {
        log.info("Received streaming query request: {}", request.query());
        // Implementation will be added in later tasks
        return Flux.just("Streaming response placeholder");
    }
}
