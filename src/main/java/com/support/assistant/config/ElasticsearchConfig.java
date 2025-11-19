package com.support.assistant.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

/**
 * Configuration for Elasticsearch client and connection.
 * Enables keyword search capabilities using BM25 algorithm.
 */
@Configuration
@EnableElasticsearchRepositories(basePackages = "com.support.assistant.repository")
@Slf4j
public class ElasticsearchConfig extends ElasticsearchConfiguration {

    @Value("${elasticsearch.host:localhost:9200}")
    private String elasticsearchHost;

    @Value("${elasticsearch.username:}")
    private String username;

    @Value("${elasticsearch.password:}")
    private String password;

    @Override
    public ClientConfiguration clientConfiguration() {
        ClientConfiguration.MaybeSecureClientConfigurationBuilder builder = 
            ClientConfiguration.builder()
                .connectedTo(elasticsearchHost);

        // Add authentication if credentials are provided
        if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
            builder.withBasicAuth(username, password);
            log.info("Elasticsearch authentication configured for user: {}", username);
        }

        ClientConfiguration config = builder.build();
        log.info("Elasticsearch client configured for host: {}", elasticsearchHost);
        
        return config;
    }
}
