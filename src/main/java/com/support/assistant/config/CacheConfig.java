package com.support.assistant.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/**
 * Configuration for caching frequently embedded queries.
 * Uses in-memory cache for development; can be replaced with Redis for production.
 */
@Configuration
@EnableCaching
@Slf4j
public class CacheConfig {

    /**
     * Configures cache manager with query embeddings cache.
     * 
     * @return configured CacheManager
     */
    @Bean
    public CacheManager cacheManager() {
        log.info("Initializing cache manager for embedding caching");
        
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(Arrays.asList(
                new ConcurrentMapCache("queryEmbeddings")
        ));
        
        log.info("Cache manager configured with queryEmbeddings cache");
        return cacheManager;
    }
}
