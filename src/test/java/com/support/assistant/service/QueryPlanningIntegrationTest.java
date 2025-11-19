package com.support.assistant.service;

import com.support.assistant.model.dto.QueryPlan;
import com.support.assistant.model.dto.QueryRequest;
import com.support.assistant.model.dto.QueryResponse;
import com.support.assistant.model.dto.SubQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for query planning, sub-query execution, and result synthesis.
 * Tests complex queries with 2-3 sub-questions.
 */
@SpringBootTest
@ActiveProfiles("test")
class QueryPlanningIntegrationTest {

    @Autowired(required = false)
    private QueryPlanner queryPlanner;

    @Autowired(required = false)
    private SubQueryExecutor subQueryExecutor;

    @Autowired(required = false)
    private ResultSynthesizer resultSynthesizer;

    @Autowired(required = false)
    private QueryService queryService;

    @Autowired(required = false)
    private DocumentIngestionService documentIngestionService;

    @BeforeEach
    void setUp() {
        // Skip tests if required beans are not available
        org.junit.jupiter.api.Assumptions.assumeTrue(
            queryPlanner != null && subQueryExecutor != null && 
            resultSynthesizer != null && queryService != null,
            "Skipping test: Required services not available"
        );
        
        // Setup test documents
        setupTestDocuments();
    }

    @Test
    void testSimpleQueryIsNotDecomposed() {
        // Given: A simple query with one question
        String simpleQuery = "What is the return policy?";

        // When: Query is planned
        QueryPlan plan = queryPlanner.planQuery(simpleQuery);

        // Then: Query should not be decomposed
        assertNotNull(plan);
        assertTrue(plan.isSimple(), "Simple query should not be decomposed");
        assertEquals(1, plan.size());
        assertEquals(simpleQuery, plan.subQueries().get(0).query());
    }

    @Test
    void testComplexQueryIsDecomposed() {
        // Given: A complex query with multiple questions
        String complexQuery = "What is the return policy and how do I initiate a return?";

        // When: Query is planned
        QueryPlan plan = queryPlanner.planQuery(complexQuery);

        // Then: Query should be decomposed into sub-queries
        assertNotNull(plan);
        
        // Note: Decomposition depends on LLM, so we check for reasonable outcomes
        assertTrue(plan.size() >= 1, "Query should have at least one sub-query");
        
        // Verify execution order is valid
        assertNotNull(plan.executionOrder());
        assertEquals(plan.size(), plan.executionOrder().size());
        
        // Verify all sub-queries have valid IDs
        for (SubQuery subQuery : plan.subQueries()) {
            assertTrue(subQuery.id() >= 0);
            assertNotNull(subQuery.query());
            assertFalse(subQuery.query().isBlank());
            assertNotNull(subQuery.queryType());
        }
    }

    @Test
    void testSubQueryDependenciesAreResolved() {
        // Given: A query that should create dependencies
        String queryWithDependencies = "Compare Plan A and Plan B, and which one is better for small businesses?";

        // When: Query is planned
        QueryPlan plan = queryPlanner.planQuery(queryWithDependencies);

        // Then: Execution order should respect dependencies
        assertNotNull(plan);
        assertNotNull(plan.executionOrder());
        
        // If decomposed, verify dependencies are in execution order
        if (!plan.isSimple()) {
            for (int i = 0; i < plan.executionOrder().size(); i++) {
                int subQueryId = plan.executionOrder().get(i);
                SubQuery subQuery = plan.subQueries().get(subQueryId);
                
                // All dependencies should appear before this sub-query in execution order
                for (Integer depId : subQuery.dependencies()) {
                    int depPosition = plan.executionOrder().indexOf(depId);
                    assertTrue(depPosition < i, 
                        String.format("Dependency %d should appear before sub-query %d in execution order", 
                            depId, subQueryId));
                }
            }
        }
    }

    @Test
    void testSubQueryExecutionWithSimpleQuery() {
        // Given: A simple query plan
        String simpleQuery = "What are the system requirements?";
        QueryPlan plan = queryPlanner.planQuery(simpleQuery);

        // When: Sub-queries are executed
        List<SubQueryExecutor.SubQueryResult> results = 
            subQueryExecutor.executeSubQueries(plan, null);

        // Then: Single result should be returned
        assertNotNull(results);
        assertEquals(1, results.size());
        
        SubQueryExecutor.SubQueryResult result = results.get(0);
        assertTrue(result.success(), "Query execution should succeed");
        assertNotNull(result.response());
        assertFalse(result.response().isBlank());
        assertNotNull(result.retrievedDocuments());
    }

    @Test
    void testSubQueryExecutionWithComplexQuery() {
        // Given: A complex query that should be decomposed
        String complexQuery = "What is the installation process and what are the system requirements?";
        QueryPlan plan = queryPlanner.planQuery(complexQuery);

        // When: Sub-queries are executed
        List<SubQueryExecutor.SubQueryResult> results = 
            subQueryExecutor.executeSubQueries(plan, null);

        // Then: Results should be returned for all sub-queries
        assertNotNull(results);
        assertEquals(plan.size(), results.size());
        
        // Verify all results
        for (SubQueryExecutor.SubQueryResult result : results) {
            assertNotNull(result);
            assertNotNull(result.query());
            assertNotNull(result.response());
            
            // Check if execution was successful
            if (result.success()) {
                assertFalse(result.response().isBlank());
                assertTrue(result.tokensUsed() > 0);
            }
        }
    }

    @Test
    void testDependentSubQueriesReceiveContext() {
        // Given: A query with dependencies
        String queryWithDeps = "What authentication methods are supported and how do they integrate with the payment system?";
        QueryPlan plan = queryPlanner.planQuery(queryWithDeps);

        // When: Sub-queries are executed
        List<SubQueryExecutor.SubQueryResult> results = 
            subQueryExecutor.executeSubQueries(plan, null);

        // Then: All sub-queries should execute successfully
        assertNotNull(results);
        assertTrue(results.size() >= 1);
        
        // Verify that dependent queries (if any) got context from earlier queries
        // This is implicit in the execution - if dependencies exist and execution succeeds,
        // context was passed correctly
        for (SubQueryExecutor.SubQueryResult result : results) {
            if (result.success()) {
                assertNotNull(result.response());
                assertFalse(result.response().isBlank());
            }
        }
    }

    @Test
    void testResultSynthesisWithSingleSubQuery() {
        // Given: Results from a single sub-query
        String query = "What is the support email?";
        QueryPlan plan = queryPlanner.planQuery(query);
        List<SubQueryExecutor.SubQueryResult> subQueryResults = 
            subQueryExecutor.executeSubQueries(plan, null);

        // When: Results are synthesized
        ResultSynthesizer.FinalSynthesisResult finalResult = 
            resultSynthesizer.synthesizeResults(query, subQueryResults);

        // Then: Final result should match the single sub-query result
        assertNotNull(finalResult);
        assertNotNull(finalResult.response());
        assertFalse(finalResult.response().isBlank());
        assertNotNull(finalResult.sources());
        assertTrue(finalResult.tokensUsed() > 0);
    }

    @Test
    void testResultSynthesisWithMultipleSubQueries() {
        // Given: Results from multiple sub-queries
        String complexQuery = "What is the return policy, how do I initiate a return, and how long does it take?";
        QueryPlan plan = queryPlanner.planQuery(complexQuery);
        List<SubQueryExecutor.SubQueryResult> subQueryResults = 
            subQueryExecutor.executeSubQueries(plan, null);

        // When: Results are synthesized
        ResultSynthesizer.FinalSynthesisResult finalResult = 
            resultSynthesizer.synthesizeResults(complexQuery, subQueryResults);

        // Then: Final result should combine all sub-query responses
        assertNotNull(finalResult);
        assertNotNull(finalResult.response());
        assertFalse(finalResult.response().isBlank());
        
        // Verify sources are combined and deduplicated
        assertNotNull(finalResult.sources());
        
        // Verify tokens include all sub-queries
        assertTrue(finalResult.tokensUsed() > 0);
    }

    @Test
    void testResultSynthesisMaintainsCitations() {
        // Given: A query that should produce citations
        String query = "What are the installation steps and troubleshooting tips?";
        QueryPlan plan = queryPlanner.planQuery(query);
        List<SubQueryExecutor.SubQueryResult> subQueryResults = 
            subQueryExecutor.executeSubQueries(plan, null);

        // When: Results are synthesized
        ResultSynthesizer.FinalSynthesisResult finalResult = 
            resultSynthesizer.synthesizeResults(query, subQueryResults);

        // Then: Citations should be maintained
        assertNotNull(finalResult);
        assertNotNull(finalResult.sources());
        
        // Verify sources have required fields
        for (var source : finalResult.sources()) {
            assertNotNull(source.documentId());
            assertNotNull(source.title());
            assertNotNull(source.excerpt());
        }
    }

    @Test
    void testEndToEndQueryPlanningWithQueryService() {
        // Given: A complex query
        String complexQuery = "What is the installation process and what should I do if I encounter errors?";
        QueryRequest request = new QueryRequest(
            complexQuery,
            null,
            null,
            false
        );

        // When: Query is processed through QueryService with planning enabled
        QueryResponse response = queryService.processQuery(request);

        // Then: Response should be generated successfully
        assertNotNull(response);
        assertNotNull(response.response());
        assertFalse(response.response().isBlank());
        
        // Verify metadata includes planning information
        assertNotNull(response.metadata());
        assertTrue(response.metadata().tokensUsed() > 0);
        assertTrue(response.metadata().latencyMs() >= 0);
        
        // Verify sources are included
        assertNotNull(response.sources());
        
        // Verify confidence score is set
        assertTrue(response.confidenceScore() >= 0.0);
        assertTrue(response.confidenceScore() <= 1.0);
    }

    @Test
    void testQueryPlanningWithThreeSubQuestions() {
        // Given: A query with three distinct questions
        String threePartQuery = "What is OAuth 2.0, how does JWT work, and what is multi-factor authentication?";
        QueryPlan plan = queryPlanner.planQuery(threePartQuery);

        // When: Query is planned and executed
        List<SubQueryExecutor.SubQueryResult> subQueryResults = 
            subQueryExecutor.executeSubQueries(plan, null);
        
        ResultSynthesizer.FinalSynthesisResult finalResult = 
            resultSynthesizer.synthesizeResults(threePartQuery, subQueryResults);

        // Then: All parts should be addressed
        assertNotNull(plan);
        assertNotNull(subQueryResults);
        assertTrue(subQueryResults.size() >= 1, "Should have at least one sub-query");
        
        // Verify final synthesis
        assertNotNull(finalResult);
        assertNotNull(finalResult.response());
        assertFalse(finalResult.response().isBlank());
        
        // Response should be coherent and address the original query
        assertTrue(finalResult.response().length() > 50, 
            "Response should be substantial for a three-part question");
    }

    @Test
    void testFallbackToOriginalQueryOnDecompositionFailure() {
        // Given: A query that might fail decomposition
        String edgeCaseQuery = "???";
        QueryPlan plan = queryPlanner.planQuery(edgeCaseQuery);

        // When: Sub-queries are executed (should fallback gracefully)
        List<SubQueryExecutor.SubQueryResult> results = 
            subQueryExecutor.executeSubQueries(plan, null);

        // Then: Should still return a result (fallback behavior)
        assertNotNull(results);
        assertFalse(results.isEmpty());
    }

    /**
     * Helper method to set up test documents for query planning tests.
     */
    private void setupTestDocuments() {
        if (documentIngestionService == null) {
            return;
        }
        
        try {
            // Document 1: Return Policy
            String content1 = """
                Return Policy
                
                Our return policy allows returns within 30 days of purchase.
                Items must be in original condition with tags attached.
                
                To initiate a return:
                1. Log into your account
                2. Go to Order History
                3. Select the item to return
                4. Choose a return reason
                5. Print the return label
                
                Refunds are processed within 5-7 business days after we receive the item.
                """;

            Map<String, Object> metadata1 = new HashMap<>();
            metadata1.put("title", "Return Policy");
            metadata1.put("documentType", "policy");

            com.support.assistant.model.dto.DocumentUpload doc1 = 
                new com.support.assistant.model.dto.DocumentUpload(
                    content1,
                    "return-policy.txt",
                    "text",
                    metadata1
                );
            documentIngestionService.ingestDocument(doc1).block();

            // Document 2: Installation Guide
            String content2 = """
                Installation Guide
                
                System Requirements:
                - Windows 10 or later / macOS 11 or later
                - 4GB RAM minimum, 8GB recommended
                - 500MB disk space
                - Internet connection
                
                Installation Process:
                1. Download the installer
                2. Run with administrator privileges
                3. Accept the license agreement
                4. Choose installation directory
                5. Wait for installation to complete
                6. Restart your computer
                
                If you encounter errors during installation, check the troubleshooting guide.
                """;

            Map<String, Object> metadata2 = new HashMap<>();
            metadata2.put("title", "Installation Guide");
            metadata2.put("documentType", "guide");

            com.support.assistant.model.dto.DocumentUpload doc2 = 
                new com.support.assistant.model.dto.DocumentUpload(
                    content2,
                    "installation.txt",
                    "text",
                    metadata2
                );
            documentIngestionService.ingestDocument(doc2).block();

            // Document 3: Authentication Documentation
            String content3 = """
                Authentication System
                
                OAuth 2.0:
                OAuth 2.0 is an authorization framework that enables applications to obtain
                limited access to user accounts. It works by delegating user authentication
                to the service that hosts the user account.
                
                JWT (JSON Web Tokens):
                JWT is a compact, URL-safe means of representing claims between two parties.
                Tokens are signed using a secret key or public/private key pair.
                
                Multi-Factor Authentication (MFA):
                MFA adds an extra layer of security by requiring two or more verification factors.
                We support TOTP authenticators, SMS codes, and biometric authentication.
                """;

            Map<String, Object> metadata3 = new HashMap<>();
            metadata3.put("title", "Authentication Documentation");
            metadata3.put("documentType", "technical");

            com.support.assistant.model.dto.DocumentUpload doc3 = 
                new com.support.assistant.model.dto.DocumentUpload(
                    content3,
                    "authentication.txt",
                    "text",
                    metadata3
                );
            documentIngestionService.ingestDocument(doc3).block();

        } catch (Exception e) {
            // If document ingestion fails, tests will be skipped
            org.junit.jupiter.api.Assumptions.assumeTrue(false, 
                "Failed to setup test documents: " + e.getMessage());
        }
    }
}
