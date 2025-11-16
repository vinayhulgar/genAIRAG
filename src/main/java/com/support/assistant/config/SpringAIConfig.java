package com.support.assistant.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI configuration
 * Specific AI integrations will be configured in later tasks
 */
@Configuration
@Slf4j
public class SpringAIConfig {

    public SpringAIConfig() {
        log.info("Spring AI configuration initialized");
    }
}
