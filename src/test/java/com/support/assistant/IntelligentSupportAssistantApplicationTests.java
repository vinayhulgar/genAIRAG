package com.support.assistant;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@EnableAutoConfiguration(exclude = {
    org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration.class,
    org.springframework.ai.autoconfigure.vectorstore.weaviate.WeaviateVectorStoreAutoConfiguration.class
})
class IntelligentSupportAssistantApplicationTests {

    @Test
    void contextLoads() {
        // Verify that the Spring context loads successfully
    }
}
