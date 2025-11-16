package com.support.assistant.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for generating embeddings using Spring AI EmbeddingModel.
 * Provides caching for frequently embedded queries to reduce API calls and costs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    /**
     * Generates embeddings for a single text query.
     * Results are cached to avoid redundant API calls for identical queries.
     *
     * @param text the text to embed
     * @return embedding vector as float array
     */
    @Cacheable(value = "queryEmbeddings", key = "#text")
    public Mono<float[]> embedQuery(String text) {
        log.debug("Generating embedding for query: {}", text.substring(0, Math.min(50, text.length())));
        
        return Mono.fromCallable(() -> {
            EmbeddingResponse response = embeddingModel.call(
                    new EmbeddingRequest(List.of(text), null)
            );
            float[] result = response.getResults().get(0).getOutput();
            log.debug("Generated embedding with dimension: {}", result.length);
            return result;
        });
    }

    /**
     * Generates embeddings for multiple documents in batch.
     * More efficient than individual calls for bulk operations.
     *
     * @param documents list of documents to embed
     * @return list of embedding vectors
     */
    public Mono<List<float[]>> embedDocuments(List<Document> documents) {
        log.debug("Generating embeddings for {} documents", documents.size());
        
        return Mono.fromCallable(() -> {
            List<String> texts = documents.stream()
                    .map(Document::getContent)
                    .collect(Collectors.toList());
            
            EmbeddingRequest request = new EmbeddingRequest(texts, null);
            EmbeddingResponse response = embeddingModel.call(request);
            
            List<float[]> embeddings = response.getResults().stream()
                    .map(embedding -> embedding.getOutput())
                    .collect(Collectors.toList());
            
            log.info("Generated {} embeddings successfully", embeddings.size());
            return embeddings;
        });
    }

    /**
     * Generates embedding for a single document.
     *
     * @param document the document to embed
     * @return embedding vector
     */
    public Mono<float[]> embedDocument(Document document) {
        log.debug("Generating embedding for document: {}", document.getId());
        
        return Mono.fromCallable(() -> {
            EmbeddingResponse response = embeddingModel.call(
                    new EmbeddingRequest(List.of(document.getContent()), null)
            );
            float[] result = response.getResults().get(0).getOutput();
            log.debug("Generated embedding for document {} with dimension: {}", 
                    document.getId(), result.length);
            return result;
        });
    }

    /**
     * Gets the dimension of embeddings produced by the model.
     *
     * @return embedding dimension
     */
    public Mono<Integer> getEmbeddingDimension() {
        return Mono.fromCallable(() -> {
            // Generate a test embedding to determine dimension
            EmbeddingResponse response = embeddingModel.call(
                    new EmbeddingRequest(List.of("test"), null)
            );
            return response.getResults().get(0).getOutput().length;
        });
    }
}
