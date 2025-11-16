package com.support.assistant.config;

import io.weaviate.client.Config;
import io.weaviate.client.WeaviateClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.WeaviateVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for Weaviate vector database integration.
 * Provides VectorStore abstraction for document embeddings storage and retrieval.
 */
@Configuration
@Slf4j
public class WeaviateConfig {
    
    /**
     * Checks if Weaviate configuration is enabled.
     * Only creates beans if an EmbeddingModel is available.
     */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnBean(EmbeddingModel.class)
    public WeaviateConfigEnabled weaviateConfigEnabled() {
        return new WeaviateConfigEnabled();
    }
    
    /**
     * Marker class to indicate Weaviate configuration is enabled.
     */
    public static class WeaviateConfigEnabled {
    }

    @Value("${weaviate.scheme:http}")
    private String scheme;

    @Value("${weaviate.host:localhost:8080}")
    private String host;

    @Value("${weaviate.api-key:}")
    private String apiKey;

    @Value("${weaviate.class-name:SupportDocument}")
    private String className;

    /**
     * Creates and configures the Weaviate client.
     * 
     * @return configured WeaviateClient instance
     */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnBean(EmbeddingModel.class)
    public WeaviateClient weaviateClient() {
        log.info("Initializing Weaviate client with host: {}://{}", scheme, host);
        
        Config config;
        if (apiKey != null && !apiKey.isEmpty()) {
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + apiKey);
            config = new Config(scheme, host, headers);
            log.debug("Weaviate client configured with API key authentication");
        } else {
            config = new Config(scheme, host);
            log.debug("Weaviate client configured without authentication");
        }
        
        return new WeaviateClient(config);
    }

    /**
     * Creates the VectorStore bean using Spring AI's Weaviate integration.
     * This provides a unified interface for vector operations.
     * 
     * @param weaviateClient the configured Weaviate client
     * @param embeddingModel the embedding model for generating vectors
     * @return VectorStore implementation for Weaviate
     */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnBean(EmbeddingModel.class)
    public VectorStore vectorStore(WeaviateClient weaviateClient, EmbeddingModel embeddingModel) {
        log.info("Creating VectorStore with class name: {}", className);
        
        WeaviateVectorStore.WeaviateVectorStoreConfig config = WeaviateVectorStore.WeaviateVectorStoreConfig
            .builder()
            .withObjectClass(className)
            .withConsistencyLevel(WeaviateVectorStore.WeaviateVectorStoreConfig.ConsistentLevel.QUORUM)
            .build();
        
        return new WeaviateVectorStore(config, embeddingModel, weaviateClient);
    }
}
