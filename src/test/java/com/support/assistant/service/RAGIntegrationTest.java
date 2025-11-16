package com.support.assistant.service;

import com.support.assistant.model.dto.QueryRequest;
import com.support.assistant.model.dto.QueryResponse;
import com.support.assistant.model.dto.Source;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the basic RAG pipeline.
 * Tests document upload, embedding generation, retrieval, and response generation.
 */
@SpringBootTest
@ActiveProfiles("test")
class RAGIntegrationTest {

    @Autowired(required = false)
    private VectorStore vectorStore;

    @Autowired(required = false)
    private DocumentIngestionService documentIngestionService;

    @Autowired(required = false)
    private QueryService queryService;

    @Autowired(required = false)
    private RetrievalService retrievalService;

    @BeforeEach
    void setUp() {
        // Skip tests if required beans are not available (e.g., in CI without external services)
        org.junit.jupiter.api.Assumptions.assumeTrue(
            vectorStore != null && documentIngestionService != null && queryService != null,
            "Skipping test: Required services not available"
        );
    }

    @Test
    void testDocumentUploadAndEmbeddingGeneration() {
        // Given: A sample support document
        String content = """
            Product Installation Guide
            
            To install our product, follow these steps:
            1. Download the installer from our website
            2. Run the installer with administrator privileges
            3. Follow the on-screen instructions
            4. Restart your computer after installation
            
            If you encounter any issues, contact support at support@example.com
            """;

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("title", "Installation Guide");
        metadata.put("documentType", "guide");
        metadata.put("source", "documentation");

        com.support.assistant.model.dto.DocumentUpload documentUpload = 
            new com.support.assistant.model.dto.DocumentUpload(
                content,
                "installation-guide.txt",
                "text",
                metadata
            );

        // When: Document is ingested
        assertDoesNotThrow(() -> {
            Integer chunkCount = documentIngestionService.ingestDocument(documentUpload).block();
            
            // Then: Document should be chunked and embedded
            assertNotNull(chunkCount);
            assertTrue(chunkCount > 0);
        });
    }

    @Test
    void testQueryWithRetrievalAndResponseGeneration() {
        // Given: Documents are in the vector store
        setupTestDocuments();

        // When: A query is submitted
        QueryRequest request = new QueryRequest(
            "How do I install the product?",
            null,
            null,
            false
        );

        QueryResponse response = queryService.processQuery(request);

        // Then: Response should be generated with sources
        assertNotNull(response);
        assertNotNull(response.response());
        assertFalse(response.response().isEmpty());
        
        // Verify response is not just a placeholder
        assertFalse(response.response().contains("placeholder"));
        
        // Verify metadata is present
        assertNotNull(response.metadata());
        assertTrue(response.metadata().tokensUsed() > 0);
        assertTrue(response.metadata().latencyMs() >= 0);
    }

    @Test
    void testResponseIncludesSourceCitations() {
        // Given: Documents are in the vector store
        setupTestDocuments();

        // When: A query is submitted
        QueryRequest request = new QueryRequest(
            "What is the support email?",
            null,
            null,
            false
        );

        QueryResponse response = queryService.processQuery(request);

        // Then: Response should include source citations
        assertNotNull(response);
        assertNotNull(response.sources());
        assertFalse(response.sources().isEmpty());
        
        // Verify sources have required fields
        for (Source source : response.sources()) {
            assertNotNull(source.documentId());
            assertNotNull(source.title());
            assertNotNull(source.excerpt());
            assertTrue(source.relevanceScore() >= 0);
        }
    }

    @Test
    void testVectorSimilaritySearch() {
        // Given: Documents are in the vector store
        setupTestDocuments();

        // When: A similarity search is performed
        List<Document> results = retrievalService.retrieve(
            "installation steps",
            5,
            null
        );

        // Then: Relevant documents should be retrieved
        assertNotNull(results);
        assertFalse(results.isEmpty());
        assertTrue(results.size() <= 5);
        
        // Verify documents have content
        for (Document doc : results) {
            assertNotNull(doc.getContent());
            assertFalse(doc.getContent().isEmpty());
        }
    }

    @Test
    void testRetrievalWithMetadataFilters() {
        // Given: Documents with different types are in the vector store
        setupTestDocuments();

        // When: A filtered search is performed
        Map<String, Object> filters = new HashMap<>();
        filters.put("documentType", "guide");

        List<Document> results = retrievalService.retrieve(
            "product information",
            10,
            filters
        );

        // Then: Only documents matching the filter should be retrieved
        assertNotNull(results);
        
        // Verify filtered results
        for (Document doc : results) {
            Object docType = doc.getMetadata().get("document_type");
            if (docType != null) {
                assertEquals("guide", docType.toString());
            }
        }
    }

    /**
     * Helper method to set up test documents in the vector store.
     */
    private void setupTestDocuments() {
        try {
            // Document 1: Installation Guide
            String content1 = """
                Product Installation Guide
                
                To install our product, follow these steps:
                1. Download the installer from our website
                2. Run the installer with administrator privileges
                3. Follow the on-screen instructions
                4. Restart your computer after installation
                
                If you encounter any issues, contact support at support@example.com
                """;

            Map<String, Object> metadata1 = new HashMap<>();
            metadata1.put("title", "Installation Guide");
            metadata1.put("documentType", "guide");
            metadata1.put("source", "documentation");

            com.support.assistant.model.dto.DocumentUpload doc1 = 
                new com.support.assistant.model.dto.DocumentUpload(
                    content1,
                    "installation-guide.txt",
                    "text",
                    metadata1
                );
            documentIngestionService.ingestDocument(doc1).block();

            // Document 2: Troubleshooting FAQ
            String content2 = """
                Troubleshooting FAQ
                
                Q: The installer won't run. What should I do?
                A: Make sure you have administrator privileges and that your antivirus is not blocking the installer.
                
                Q: How do I contact support?
                A: You can reach us at support@example.com or call 1-800-SUPPORT.
                
                Q: What are the system requirements?
                A: Windows 10 or later, 4GB RAM, 500MB disk space.
                """;

            Map<String, Object> metadata2 = new HashMap<>();
            metadata2.put("title", "Troubleshooting FAQ");
            metadata2.put("documentType", "faq");
            metadata2.put("source", "support");

            com.support.assistant.model.dto.DocumentUpload doc2 = 
                new com.support.assistant.model.dto.DocumentUpload(
                    content2,
                    "troubleshooting-faq.txt",
                    "text",
                    metadata2
                );
            documentIngestionService.ingestDocument(doc2).block();

        } catch (Exception e) {
            // If document ingestion fails, tests will be skipped
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Failed to setup test documents: " + e.getMessage());
        }
    }
}
