package com.support.assistant.service;

import com.support.assistant.model.dto.QueryRequest;
import com.support.assistant.model.dto.QueryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Service for processing queries
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QueryService {

    public Mono<QueryResponse> processQuery(QueryRequest request) {
        log.info("Processing query: {}", request.query());
        // Implementation will be added in later tasks
        return Mono.just(new QueryResponse(
            "Placeholder response",
            null,
            0.0,
            null
        ));
    }
}
