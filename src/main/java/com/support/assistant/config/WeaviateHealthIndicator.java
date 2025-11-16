package com.support.assistant.config;

import io.weaviate.client.WeaviateClient;
import io.weaviate.client.v1.misc.model.Meta;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator for Weaviate vector database connection.
 * Checks connectivity and provides version information.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@org.springframework.boot.autoconfigure.condition.ConditionalOnBean(WeaviateClient.class)
public class WeaviateHealthIndicator implements HealthIndicator {

    private final WeaviateClient weaviateClient;

    @Override
    public Health health() {
        try {
            // Attempt to get meta information from Weaviate
            Meta meta = weaviateClient.misc().metaGetter().run().getResult();
            
            if (meta != null) {
                log.debug("Weaviate health check successful. Version: {}", meta.getVersion());
                return Health.up()
                    .withDetail("version", meta.getVersion())
                    .withDetail("hostname", meta.getHostname())
                    .build();
            } else {
                log.warn("Weaviate health check returned null meta");
                return Health.down()
                    .withDetail("reason", "Unable to retrieve meta information")
                    .build();
            }
        } catch (Exception e) {
            log.error("Weaviate health check failed", e);
            return Health.down()
                .withDetail("error", e.getClass().getSimpleName())
                .withDetail("message", e.getMessage())
                .build();
        }
    }
}
