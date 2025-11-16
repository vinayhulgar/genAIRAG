package com.support.assistant.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Spring AI configuration for LLM integration.
 * Supports AWS Bedrock (primary) and OpenAI (fallback).
 */
@Configuration
@Slf4j
public class SpringAIConfig {

    public SpringAIConfig() {
        log.info("Spring AI configuration initialized");
    }

    /**
     * Creates ChatClient for AWS Bedrock (primary).
     * Activated when bedrock configuration is present.
     */
    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "spring.ai.bedrock.aws", name = "region")
    public ChatClient bedrockChatClient(@Qualifier("bedrockChatModel") ChatModel chatModel) {
        log.info("Configuring ChatClient with AWS Bedrock");
        return ChatClient.builder(chatModel).build();
    }

    /**
     * Creates ChatClient for OpenAI (fallback).
     * Activated when OpenAI API key is present and Bedrock is not configured.
     */
    @Bean
    @ConditionalOnProperty(prefix = "spring.ai.openai", name = "api-key")
    public ChatClient openaiChatClient(@Qualifier("openAiChatModel") ChatModel chatModel) {
        log.info("Configuring ChatClient with OpenAI");
        return ChatClient.builder(chatModel).build();
    }
}
