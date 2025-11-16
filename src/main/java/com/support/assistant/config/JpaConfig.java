package com.support.assistant.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * JPA configuration
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.support.assistant.repository")
@EnableTransactionManagement
public class JpaConfig {
}
