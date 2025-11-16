package com.support.assistant.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for embedding model integration.
 * Supports OpenAI embeddings with configurable model selection.
 */
@Configuration
@Slf4j
public class EmbeddingConfig {

    @Value("${spring.ai.openai.api-key:}")
    private String openAiApiKey;

    @Value("${spring.ai.openai.embedding.model:text-embedding-3-small}")
    private String embeddingModelName;

    /**
     * Creates OpenAI embedding model bean.
     * Uses text-embedding-3-small by default for cost-effectiveness.
     * 
     * @return configured EmbeddingModel instance
     */
    @Bean
    @ConditionalOnProperty(name = "spring.ai.openai.api-key")
    public EmbeddingModel embeddingModel() {
        log.info("Initializing OpenAI embedding model: {}", embeddingModelName);
        
        if (openAiApiKey == null || openAiApiKey.isEmpty()) {
            log.warn("OpenAI API key not configured. Embedding model will not be available.");
            return null;
        }
        
        OpenAiApi openAiApi = new OpenAiApi(openAiApiKey);
        
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .withModel(embeddingModelName)
                .build();
        
        log.info("OpenAI embedding model configured successfully");
        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, options);
    }
}
