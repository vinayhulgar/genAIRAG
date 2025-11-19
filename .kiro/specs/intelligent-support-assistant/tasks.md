# Implementation Plan

This plan is structured in phases to teach you advanced AI/ML concepts progressively. Each phase builds on the previous one, allowing you to learn and validate before moving forward.

## Phase 1: Foundation & Basic RAG

- [x] 1. Set up Spring Boot project with core dependencies
  - Create Spring Boot 3.2+ project with Java 21
  - Add dependencies: Spring AI, Spring WebFlux, Spring Data JPA, Lombok
  - Configure application.yml with profiles (dev, prod)
  - Set up project structure: controllers, services, repositories, models, config
  - _Requirements: All (foundation)_
  - _Learning: Spring Boot project structure, dependency management_

- [x] 2. Implement basic vector database integration with Weaviate
  - [x] 2.1 Configure Weaviate client and connection
    - Add Weaviate Spring AI dependency
    - Create WeaviateConfig class with connection settings
    - Implement health check for Weaviate connection
    - _Requirements: 1.1_
    - _Learning: Vector database basics, Spring AI VectorStore abstraction_
  
  - [x] 2.2 Create Document model and repository
    - Implement Document entity with JPA annotations
    - Create DocumentMetadata embeddable class
    - Build DocumentRepository interface extending JpaRepository
    - _Requirements: 9.5_
    - _Learning: JPA entities, embeddings storage_
  
  - [x] 2.3 Implement document chunking service
    - Create DocumentChunker service using Spring AI TokenTextSplitter
    - Configure chunk size (500-1000 tokens) and overlap (100 tokens)
    - Implement metadata extraction (title, headers, dates)
    - _Requirements: 9.2_
    - _Learning: Text chunking strategies, token counting_

- [x] 3. Integrate embedding model and store embeddings
  - [x] 3.1 Configure embedding model (OpenAI or AWS Bedrock)
    - Create EmbeddingConfig with model selection
    - Implement EmbeddingService using Spring AI EmbeddingModel
    - Add caching for frequently embedded queries
    - _Requirements: 1.2_
    - _Learning: Embeddings, vector representations, semantic similarity_
  
  - [x] 3.2 Implement document ingestion pipeline
    - Create DocumentIngestionService that chunks, embeds, and stores documents
    - Build REST endpoint POST /api/v1/documents for document upload
    - Support PDF, Markdown, and text formats using Spring AI DocumentReader
    - _Requirements: 9.1, 9.2, 9.3_
    - _Learning: End-to-end document processing pipeline_

- [x] 4. Build basic RAG query pipeline
  - [x] 4.1 Implement vector similarity search
    - Create RetrievalService with vector search using Spring AI VectorStore
    - Implement top-k retrieval (k=10)
    - Add filtering by metadata (document type, date range)
    - _Requirements: 1.3_
    - _Learning: Vector similarity search, cosine similarity_
  
  - [x] 4.2 Integrate LLM for response generation
    - Configure AWS Bedrock with Amazon Nova using Spring AI
    - Create SynthesisService with ChatClient
    - Implement prompt template with context injection
    - Build REST endpoint POST /api/v1/query for basic queries
    - _Requirements: 5.1, 5.2, 5.3_
    - _Learning: RAG basics, prompt engineering, LLM integration_
  
  - [x] 4.3 Write integration tests for basic RAG flow
    - Test document upload and embedding generation
    - Test query with retrieval and response generation
    - Verify response includes source citations
    - _Requirements: 5.3_
    - _Learning: Testing RAG systems_

## Phase 2: Advanced Retrieval Patterns

- [x] 5. Implement hybrid search (vector + keyword)
  - [x] 5.1 Add Elasticsearch for keyword search
    - Configure Elasticsearch client with Spring Data Elasticsearch
    - Create document index with text fields for BM25 search
    - Implement keyword search service
    - _Requirements: 1.4_
    - _Learning: BM25 algorithm, full-text search, inverted indexes_
  
  - [x] 5.2 Implement Reciprocal Rank Fusion (RRF)
    - Create HybridSearchService that combines vector and keyword results
    - Implement RRF algorithm to merge ranked lists
    - Configure fusion weights (vector: 0.6, keyword: 0.4)
    - _Requirements: 1.4_
    - _Learning: Rank fusion algorithms, hybrid retrieval strategies_
  
  - [x] 5.3 Add cross-encoder reranking
    - Integrate reranking model (e.g., cross-encoder/ms-marco-MiniLM)
    - Implement RerankerService to score query-document pairs
    - Rerank top 20 results to final top 10
    - _Requirements: 1.3_
    - _Learning: Cross-encoders vs bi-encoders, reranking strategies_

- [x] 6. Implement context compression
  - [x] 6.1 Build extractive compression service
    - Create ContextCompressor that scores sentences by relevance
    - Use embedding similarity between query and sentences
    - Remove redundant information using semantic deduplication
    - _Requirements: 3.1, 3.2_
    - _Learning: Context compression techniques, token optimization_
  
  - [x] 6.2 Add token budget management
    - Implement token counting using tiktoken or similar
    - Enforce max context size (4000 tokens)
    - Log compression ratios for monitoring
    - _Requirements: 3.3, 3.5_
    - _Learning: Token management, cost optimization_

- [x] 7. Implement multi-hop RAG
  - [x] 7.1 Build entity extraction service
    - Create EntityExtractor using LLM to extract key entities from retrieved docs
    - Identify concepts that need additional context
    - _Requirements: 2.3_
    - _Learning: Entity extraction, information extraction_
  
  - [x] 7.2 Implement iterative retrieval
    - Create MultiHopRetriever that performs 2-3 retrieval rounds
    - Use extracted entities for follow-up queries
    - Combine and deduplicate results across hops
    - _Requirements: 2.3_
    - _Learning: Multi-hop reasoning, iterative retrieval_
  
  - [x] 7.3 Test multi-hop retrieval with complex queries
    - Create test cases requiring information from multiple documents
    - Verify all relevant information is retrieved
    - _Requirements: 2.3_
    - _Learning: Evaluating multi-hop systems_

## Phase 3: Query Planning & Decomposition

- [x] 8. Implement query analysis and planning
  - [x] 8.1 Build query classifier
    - Create QueryClassifier that categorizes queries (factual, comparison, procedural)
    - Use LLM with structured output (function calling)
    - _Requirements: 2.1_
    - _Learning: Query understanding, classification_
  
  - [x] 8.2 Implement query decomposition
    - Create QueryPlanner that breaks complex queries into sub-queries
    - Use LLM to identify multiple questions in a single query
    - Generate SubQuery objects with dependencies
    - _Requirements: 2.1_
    - _Learning: Query decomposition, dependency analysis_
  
  - [x] 8.3 Build dependency resolver
    - Implement topological sort for sub-query execution order
    - Create QueryPlan with ordered execution list
    - Handle cyclic dependencies gracefully
    - _Requirements: 2.2_
    - _Learning: Graph algorithms, dependency resolution_

- [x] 9. Implement sub-query execution and result synthesis
  - [x] 9.1 Execute sub-queries in order
    - Create SubQueryExecutor that processes queries sequentially
    - Pass results from earlier queries as context to later ones
    - Handle failures with fallback to original query
    - _Requirements: 2.2, 2.5_
    - _Learning: Sequential processing, context passing_
  
  - [x] 9.2 Synthesize results from multiple sub-queries
    - Create ResultSynthesizer that combines sub-query responses
    - Use LLM to create coherent final answer
    - Maintain citations from all sub-queries
    - _Requirements: 2.4_
    - _Learning: Result aggregation, multi-source synthesis_
  
  - [x] 9.3 Test query planning with complex questions
    - Test queries with 2-3 sub-questions
    - Verify correct decomposition and execution order
    - Validate final synthesized response
    - _Requirements: 2.1, 2.2, 2.4_
    - _Learning: End-to-end query planning validation_

## Phase 4: Multi-Agent Orchestration

- [x] 10. Design agent framework and state management
  - [x] 10.1 Create agent interfaces and base classes
    - Define Agent interface with execute() method
    - Create AgentState class to hold workflow state
    - Implement AgentContext for passing data between agents
    - _Requirements: 4.1_
    - _Learning: Agent design patterns, state management_
  
  - [x] 10.2 Implement specialized agents
    - Create PlannerAgent (query decomposition)
    - Create RetrieverAgent (hybrid search + multi-hop)
    - Create SynthesizerAgent (response generation)
    - Create ValidatorAgent (hallucination detection)
    - _Requirements: 4.1_
    - _Learning: Agent specialization, separation of concerns_

- [x] 11. Build agent orchestrator with Spring State Machine
  - [x] 11.1 Configure state machine workflow
    - Define workflow states (PLANNING, RETRIEVING, COMPRESSING, GENERATING, VALIDATING)
    - Configure state transitions and guards
    - Implement event-driven state changes
    - _Requirements: 4.2, 4.3_
    - _Learning: State machines, workflow orchestration_
  
  - [x] 11.2 Implement agent routing and coordination
    - Create AgentOrchestrator that manages agent execution
    - Route queries to appropriate agents based on type
    - Pass AgentState between agents
    - _Requirements: 4.2, 4.3_
    - _Learning: Agent coordination, message passing_
  
  - [x] 11.3 Add retry and fallback logic
    - Implement retry mechanism for failed agents (max 3 attempts)
    - Add fallback strategies (e.g., skip compression if it fails)
    - Use Spring @Retryable annotation
    - _Requirements: 4.4_
    - _Learning: Resilience patterns, error handling_

- [ ] 12. Implement async agent execution
  - [ ] 12.1 Add reactive processing with WebFlux
    - Convert services to return Mono/Flux for async execution
    - Implement parallel retrieval for independent sub-queries
    - Use CompletableFuture for agent coordination
    - _Requirements: 8.1, 8.3_
    - _Learning: Reactive programming, async patterns_
  
  - [ ] 12.2 Build streaming response endpoint
    - Create POST /api/v1/query/stream endpoint
    - Stream response tokens as they're generated using Server-Sent Events
    - Update AgentState progressively
    - _Requirements: 8.2_
    - _Learning: Streaming responses, SSE, progressive rendering_
  
  - [ ] 12.3 Test concurrent query handling
    - Simulate 50-100 concurrent queries
    - Verify no resource contention or deadlocks
    - Measure throughput and latency
    - _Requirements: 8.3_
    - _Learning: Concurrency testing, load testing_

## Phase 5: Hallucination Detection & Validation

- [ ] 13. Implement hallucination detection
  - [ ] 13.1 Build entailment checker
    - Integrate NLI model (e.g., DeBERTa-v3-base-mnli)
    - Create EntailmentChecker that verifies claims against sources
    - Score each sentence in response for entailment
    - _Requirements: 6.2_
    - _Learning: Natural Language Inference, entailment_
  
  - [ ] 13.2 Implement fact extraction and verification
    - Create FactExtractor using LLM to extract factual claims
    - Build FactVerifier that checks each fact against source documents
    - Use semantic search to find supporting evidence
    - _Requirements: 6.2_
    - _Learning: Fact verification, claim detection_
  
  - [ ] 13.3 Build LLM-as-judge validator
    - Create LLMJudge that uses LLM to evaluate faithfulness
    - Prompt LLM to compare response with sources
    - Generate structured validation output
    - _Requirements: 6.2_
    - _Learning: LLM-based evaluation, meta-prompting_

- [ ] 14. Implement confidence scoring and validation
  - [ ] 14.1 Create composite confidence score
    - Combine entailment, fact verification, and semantic similarity scores
    - Weight scores: 40% entailment, 30% facts, 20% similarity, 10% LLM judge
    - Generate confidence score 0-100
    - _Requirements: 6.3_
    - _Learning: Score aggregation, confidence estimation_
  
  - [ ] 14.2 Add validation workflow
    - Integrate ValidatorAgent into orchestration
    - Flag responses with confidence < 70 for human review
    - Log all hallucinated claims
    - _Requirements: 6.4, 6.5_
    - _Learning: Validation workflows, quality gates_
  
  - [ ] 14.3 Build validation reporting
    - Create ValidationReport with detailed breakdown
    - Include hallucinated claims and verification details
    - Add to QueryResponse metadata
    - _Requirements: 6.5_
    - _Learning: Transparency, explainability_

## Phase 6: Evaluation & Metrics

- [ ] 15. Implement retrieval evaluation metrics
  - [ ] 15.1 Build retrieval metrics calculator
    - Implement Precision@k and Recall@k
    - Calculate Mean Reciprocal Rank (MRR)
    - Compute NDCG (Normalized Discounted Cumulative Gain)
    - _Requirements: 7.1_
    - _Learning: Information retrieval metrics, ranking evaluation_
  
  - [ ] 15.2 Create ground truth dataset
    - Build test dataset with 50+ query-document pairs
    - Label relevant documents for each query
    - Store in database for offline evaluation
    - _Requirements: 7.1_
    - _Learning: Dataset creation, evaluation methodology_

- [ ] 16. Implement generation evaluation metrics
  - [ ] 16.1 Build answer relevancy calculator
    - Compute semantic similarity between query and response
    - Use embedding model for similarity scoring
    - _Requirements: 7.2_
    - _Learning: Semantic similarity, answer quality_
  
  - [ ] 16.2 Implement faithfulness metric
    - Measure alignment between response and source documents
    - Use entailment scores and fact verification
    - Calculate faithfulness score 0-1
    - _Requirements: 7.2_
    - _Learning: Faithfulness evaluation, grounding_
  
  - [ ] 16.3 Add context precision and recall
    - Measure relevance of retrieved documents (context precision)
    - Measure coverage of ground truth (context recall)
    - _Requirements: 7.2_
    - _Learning: Context quality metrics_

- [ ] 17. Build evaluation engine and reporting
  - [ ] 17.1 Create EvaluationService
    - Implement EvaluationEngine that runs all metrics
    - Store evaluation results in database
    - Support both online (per-query) and offline (batch) evaluation
    - _Requirements: 7.1, 7.2_
    - _Learning: Evaluation frameworks, metrics aggregation_
  
  - [ ] 17.2 Implement metrics collection with Micrometer
    - Add Micrometer dependencies for metrics
    - Track latency (p50, p95, p99) using Timer
    - Track token usage and cost using Counter
    - Track error rates using Counter
    - _Requirements: 7.4_
    - _Learning: Observability, metrics collection_
  
  - [ ] 17.3 Build evaluation dashboard and reports
    - Create GET /api/v1/metrics endpoint for metrics retrieval
    - Generate daily performance reports
    - Export metrics to Prometheus
    - _Requirements: 7.5_
    - _Learning: Metrics visualization, reporting_
  
  - [ ] 17.4 Create evaluation test suite
    - Run offline evaluation on test dataset
    - Generate evaluation report with all metrics
    - Compare against baseline performance
    - _Requirements: 7.1, 7.2_
    - _Learning: Offline evaluation, benchmarking_

## Phase 7: Monitoring, Logging & Production Readiness

- [ ] 18. Implement comprehensive logging
  - [ ] 18.1 Add structured logging with SLF4J and Logback
    - Configure JSON logging format
    - Add correlation IDs to all logs
    - Log queries, responses, and retrieval results
    - _Requirements: 10.1_
    - _Learning: Structured logging, log aggregation_
  
  - [ ] 18.2 Implement PII detection and masking
    - Create PIIDetector using regex and NER models
    - Mask sensitive data (emails, phone numbers, names) in logs
    - _Requirements: 10.1_
    - _Learning: Data privacy, PII protection_
  
  - [ ] 18.3 Add error logging with context
    - Log full context on errors (query, retrieved docs, error details)
    - Include stack traces and agent state
    - _Requirements: 10.3_
    - _Learning: Error diagnostics, debugging_

- [ ] 19. Set up monitoring and observability
  - [ ] 19.1 Configure Spring Boot Actuator
    - Enable health, metrics, and info endpoints
    - Add custom health indicators for Weaviate, LLM, Elasticsearch
    - _Requirements: 10.4_
    - _Learning: Health checks, service monitoring_
  
  - [ ] 19.2 Integrate with AWS CloudWatch
    - Configure CloudWatch logs agent
    - Set up log groups and streams
    - Create CloudWatch alarms for errors and latency
    - _Requirements: 10.5_
    - _Learning: Cloud monitoring, alerting_
  
  - [ ] 19.3 Set up distributed tracing with X-Ray
    - Add AWS X-Ray SDK
    - Instrument services with tracing
    - Visualize request flows across agents
    - _Requirements: 10.5_
    - _Learning: Distributed tracing, request tracking_

- [ ] 20. Implement caching and performance optimization
  - [ ] 20.1 Add Redis caching layer
    - Configure Spring Cache with Redis
    - Cache query responses (TTL: 1 hour)
    - Cache embeddings for common queries
    - Cache frequently accessed documents
    - _Requirements: Performance optimization_
    - _Learning: Caching strategies, cache invalidation_
  
  - [ ] 20.2 Optimize database queries
    - Add indexes on frequently queried fields
    - Implement pagination for large result sets
    - Use connection pooling
    - _Requirements: Performance optimization_
    - _Learning: Database optimization, indexing_
  
  - [ ] 20.3 Implement batch processing for embeddings
    - Batch multiple documents for embedding generation
    - Use parallel processing for independent operations
    - _Requirements: 1.2_
    - _Learning: Batch processing, parallelization_

- [ ] 21. Build admin and management APIs
  - [ ] 21.1 Create document management endpoints
    - GET /api/v1/documents - List documents with pagination
    - GET /api/v1/documents/{id} - Get document details
    - DELETE /api/v1/documents/{id} - Delete document and embeddings
    - PUT /api/v1/documents/{id} - Update document
    - _Requirements: 9.4_
    - _Learning: CRUD operations, REST API design_
  
  - [ ] 21.2 Add configuration management endpoints
    - GET /api/v1/config - Get current configuration
    - PUT /api/v1/config - Update configuration (vector DB, LLM model)
    - _Requirements: 1.5_
    - _Learning: Dynamic configuration, feature flags_
  
  - [ ] 21.3 Build query history and analytics
    - GET /api/v1/sessions/{sessionId} - Get query session history
    - GET /api/v1/analytics - Get usage analytics
    - _Requirements: Monitoring_
    - _Learning: Analytics, usage tracking_

## Phase 8: Security & Deployment

- [ ] 22. Implement security features
  - [ ] 22.1 Add authentication with JWT
    - Configure Spring Security
    - Implement JWT token generation and validation
    - Add login endpoint POST /api/v1/auth/login
    - _Requirements: Security_
    - _Learning: JWT, authentication_
  
  - [ ] 22.2 Implement authorization and RBAC
    - Define roles (USER, ADMIN)
    - Add role-based access control to endpoints
    - Restrict document management to ADMIN role
    - _Requirements: Security_
    - _Learning: Authorization, RBAC_
  
  - [ ] 22.3 Add rate limiting
    - Implement rate limiter using Bucket4j
    - Set per-user limits (100 requests/hour)
    - Set per-IP limits (1000 requests/hour)
    - _Requirements: Security_
    - _Learning: Rate limiting, abuse prevention_

- [ ] 23. Containerization and deployment
  - [ ] 23.1 Create Dockerfile
    - Build multi-stage Dockerfile
    - Use Eclipse Temurin JDK 21 base image
    - Optimize image size with layers
    - _Requirements: Deployment_
    - _Learning: Docker, containerization_
  
  - [ ] 23.2 Write docker-compose for local development
    - Define services: app, weaviate, elasticsearch, redis, postgres
    - Configure networking and volumes
    - Add environment variables
    - _Requirements: Deployment_
    - _Learning: Docker Compose, local development_
  
  - [ ] 23.3 Create AWS ECS task definition
    - Define ECS task with container configuration
    - Configure resource limits (CPU, memory)
    - Set up environment variables from Parameter Store
    - _Requirements: Deployment_
    - _Learning: ECS, cloud deployment_
  
  - [ ] 23.4 Set up CI/CD pipeline
    - Create GitHub Actions workflow
    - Build and test on push
    - Build Docker image and push to ECR
    - Deploy to ECS on merge to main
    - _Requirements: Deployment_
    - _Learning: CI/CD, automation_

- [ ] 24. End-to-end testing and validation
  - [ ] 24.1 Create comprehensive integration tests
    - Test full workflow from document upload to query response
    - Test all agent interactions
    - Test error scenarios and fallbacks
    - _Requirements: All_
    - _Learning: Integration testing, E2E testing_
  
  - [ ] 24.2 Run load tests
    - Use JMeter or Gatling for load testing
    - Simulate 100 concurrent users
    - Measure p95 latency and throughput
    - Verify auto-scaling behavior
    - _Requirements: 8.3_
    - _Learning: Load testing, performance testing_
  
  - [ ] 24.3 Conduct security testing
    - Run OWASP ZAP security scan
    - Test authentication and authorization
    - Verify rate limiting
    - Test input validation
    - _Requirements: Security_
    - _Learning: Security testing, penetration testing_

## Phase 9: Advanced Features & Optimization

- [ ] 25. Implement alternative vector databases
  - [ ] 25.1 Add Pinecone integration
    - Configure Pinecone client
    - Implement PineconeVectorStore adapter
    - Add configuration to switch between Weaviate and Pinecone
    - _Requirements: 1.1, 1.5_
    - _Learning: Multi-provider support, adapter pattern_
  
  - [ ] 25.2 Add Elasticsearch vector search
    - Configure Elasticsearch with kNN plugin
    - Implement ElasticsearchVectorStore adapter
    - Support hybrid search natively in Elasticsearch
    - _Requirements: 1.1, 1.5_
    - _Learning: Elasticsearch vector capabilities_

- [ ] 26. Implement alternative agent orchestration patterns
  - [ ] 26.1 Explore CrewAI-style role-based agents (optional)
    - Define agent roles and goals
    - Implement task delegation between agents
    - _Requirements: 4.1_
    - _Learning: Role-based agent systems_
  
  - [ ] 26.2 Implement OpenAI Swarm patterns (optional)
    - Create lightweight agent handoffs
    - Implement function-based agent routing
    - _Requirements: 4.1_
    - _Learning: Swarm intelligence, lightweight orchestration_

- [ ] 27. Add advanced evaluation features
  - [ ] 27.1 Implement A/B testing framework
    - Create experiment configuration
    - Route queries to different strategies
    - Compare metrics between variants
    - _Requirements: Evaluation_
    - _Learning: A/B testing, experimentation_
  
  - [ ] 27.2 Build feedback collection system
    - Add thumbs up/down to responses
    - Collect user corrections
    - Store feedback for model improvement
    - _Requirements: Evaluation_
    - _Learning: User feedback, active learning_
  
  - [ ] 27.3 Implement automated evaluation pipeline
    - Schedule daily evaluation runs
    - Compare against baseline metrics
    - Alert on metric degradation
    - _Requirements: 7.5_
    - _Learning: Continuous evaluation, regression detection_
