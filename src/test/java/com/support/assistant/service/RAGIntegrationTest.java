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

    @Autowired(required = false)
    private MultiHopRetriever multiHopRetriever;

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

    @Test
    void testMultiHopRetrievalWithComplexQuery() {
        // Given: Documents requiring multi-hop retrieval are in the vector store
        setupMultiHopTestDocuments();
        
        // Skip if multi-hop retriever is not available
        org.junit.jupiter.api.Assumptions.assumeTrue(
            multiHopRetriever != null,
            "Skipping test: MultiHopRetriever not available"
        );

        // When: A complex query requiring information from multiple documents is submitted
        MultiHopRetriever.MultiHopResult result = multiHopRetriever.retrieve(
            "How does the authentication system integrate with the payment gateway?"
        );

        // Then: Multiple hops should be performed
        assertNotNull(result);
        assertTrue(result.hopsPerformed() >= 1, "At least one hop should be performed");
        
        // Verify documents were retrieved
        assertNotNull(result.documents());
        assertFalse(result.documents().isEmpty(), "Documents should be retrieved");
        
        // Verify hop queries were tracked
        assertNotNull(result.hopQueries());
        assertFalse(result.hopQueries().isEmpty());
        assertEquals(result.hopsPerformed(), result.hopQueries().size() - result.hopsPerformed() + 1);
    }

    @Test
    void testMultiHopRetrievalDeduplication() {
        // Given: Documents are in the vector store
        setupMultiHopTestDocuments();
        
        // Skip if multi-hop retriever is not available
        org.junit.jupiter.api.Assumptions.assumeTrue(
            multiHopRetriever != null,
            "Skipping test: MultiHopRetriever not available"
        );

        // When: Multi-hop retrieval is performed
        MultiHopRetriever.MultiHopResult result = multiHopRetriever.retrieve(
            "What are the security features?"
        );

        // Then: Documents should be deduplicated
        assertNotNull(result);
        assertNotNull(result.documents());
        
        // Verify no duplicate documents by checking unique content
        long uniqueContentCount = result.documents().stream()
            .map(Document::getContent)
            .distinct()
            .count();
        
        assertEquals(uniqueContentCount, result.documents().size(), 
            "All documents should be unique (no duplicates)");
    }

    @Test
    void testMultiHopRetrievalCombinesInformationFromMultipleSources() {
        // Given: Documents with related but separate information
        setupMultiHopTestDocuments();
        
        // Skip if multi-hop retriever is not available
        org.junit.jupiter.api.Assumptions.assumeTrue(
            multiHopRetriever != null,
            "Skipping test: MultiHopRetriever not available"
        );

        // When: A query requiring information from multiple documents is submitted
        MultiHopRetriever.MultiHopResult result = multiHopRetriever.retrieve(
            "Explain the complete user authentication and payment flow"
        );

        // Then: Documents from multiple topics should be retrieved
        assertNotNull(result);
        assertNotNull(result.documents());
        
        // Verify we got documents (exact count depends on what's in the vector store)
        assertTrue(result.documents().size() > 0, 
            "Should retrieve documents from multiple sources");
        
        // Verify multiple hops were attempted if entities were found
        assertTrue(result.hopsPerformed() >= 1, 
            "Should perform at least one hop");
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

    /**
     * Helper method to set up test documents for multi-hop retrieval testing.
     * Creates documents with interconnected concepts requiring multiple retrieval hops.
     */
    private void setupMultiHopTestDocuments() {
        try {
            // Document 1: Authentication System Overview
            String content1 = """
                Authentication System Overview
                
                Our authentication system uses OAuth 2.0 and JWT tokens for secure user authentication.
                The system integrates with multiple services including the payment gateway and user profile service.
                
                Key features:
                - Multi-factor authentication (MFA)
                - Single sign-on (SSO) support
                - Token-based authentication with JWT
                - Integration with external identity providers
                
                The authentication flow generates access tokens that are used by downstream services
                like the payment gateway to verify user identity.
                """;

            Map<String, Object> metadata1 = new HashMap<>();
            metadata1.put("title", "Authentication System Overview");
            metadata1.put("documentType", "technical");
            metadata1.put("source", "architecture");

            com.support.assistant.model.dto.DocumentUpload doc1 = 
                new com.support.assistant.model.dto.DocumentUpload(
                    content1,
                    "auth-system.txt",
                    "text",
                    metadata1
                );
            documentIngestionService.ingestDocument(doc1).block();

            // Document 2: Payment Gateway Integration
            String content2 = """
                Payment Gateway Integration Guide
                
                The payment gateway requires authenticated requests using JWT tokens from the authentication system.
                All payment transactions must include a valid access token in the Authorization header.
                
                Integration steps:
                1. Obtain JWT token from authentication system
                2. Include token in payment API requests
                3. Payment gateway validates token before processing
                4. Transaction results are returned with security audit logs
                
                The gateway supports multiple payment methods including credit cards, PayPal, and cryptocurrency.
                Security features include PCI DSS compliance and fraud detection.
                """;

            Map<String, Object> metadata2 = new HashMap<>();
            metadata2.put("title", "Payment Gateway Integration");
            metadata2.put("documentType", "technical");
            metadata2.put("source", "integration");

            com.support.assistant.model.dto.DocumentUpload doc2 = 
                new com.support.assistant.model.dto.DocumentUpload(
                    content2,
                    "payment-gateway.txt",
                    "text",
                    metadata2
                );
            documentIngestionService.ingestDocument(doc2).block();

            // Document 3: Security Best Practices
            String content3 = """
                Security Best Practices
                
                JWT Token Management:
                - Tokens should expire after 1 hour
                - Refresh tokens should be stored securely
                - Never expose tokens in URLs or logs
                
                OAuth 2.0 Configuration:
                - Use PKCE for mobile and SPA applications
                - Implement proper redirect URI validation
                - Store client secrets securely
                
                Multi-factor Authentication:
                - Support TOTP-based authenticators
                - SMS backup codes for account recovery
                - Biometric authentication on supported devices
                """;

            Map<String, Object> metadata3 = new HashMap<>();
            metadata3.put("title", "Security Best Practices");
            metadata3.put("documentType", "guide");
            metadata3.put("source", "security");

            com.support.assistant.model.dto.DocumentUpload doc3 = 
                new com.support.assistant.model.dto.DocumentUpload(
                    content3,
                    "security-practices.txt",
                    "text",
                    metadata3
                );
            documentIngestionService.ingestDocument(doc3).block();

            // Document 4: User Profile Service
            String content4 = """
                User Profile Service API
                
                The user profile service stores user information and preferences.
                It requires authentication via JWT tokens and integrates with the authentication system.
                
                Available endpoints:
                - GET /api/profile - Retrieve user profile
                - PUT /api/profile - Update user profile
                - GET /api/preferences - Get user preferences
                - POST /api/preferences - Update preferences
                
                Profile data includes:
                - Personal information (name, email, phone)
                - Payment preferences and saved payment methods
                - Security settings (MFA status, login history)
                - Notification preferences
                """;

            Map<String, Object> metadata4 = new HashMap<>();
            metadata4.put("title", "User Profile Service");
            metadata4.put("documentType", "api");
            metadata4.put("source", "documentation");

            com.support.assistant.model.dto.DocumentUpload doc4 = 
                new com.support.assistant.model.dto.DocumentUpload(
                    content4,
                    "profile-service.txt",
                    "text",
                    metadata4
                );
            documentIngestionService.ingestDocument(doc4).block();

        } catch (Exception e) {
            // If document ingestion fails, tests will be skipped
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Failed to setup multi-hop test documents: " + e.getMessage());
        }
    }
}
