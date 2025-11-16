# Intelligent Customer Support Knowledge Assistant

An AI-powered customer support system leveraging advanced RAG (Retrieval-Augmented Generation) patterns, vector databases, and multi-agent orchestration.

## Features

- Advanced RAG with hybrid search (vector + keyword)
- Multi-agent orchestration for complex query handling
- Hallucination detection and validation
- Real-time query processing with streaming responses
- Comprehensive evaluation and metrics

## Technology Stack

- **Java 21** with **Spring Boot 3.2+**
- **Spring AI** for LLM integration
- **Spring WebFlux** for reactive processing
- **Spring Data JPA** for persistence
- **Vector Databases**: Weaviate, Pinecone, or Elasticsearch
- **LLM Providers**: AWS Bedrock (Amazon Nova), OpenAI

## Prerequisites

- Java 21 or higher
- Maven 3.8+
- Docker (for running vector databases locally)

## Getting Started

### 1. Clone the repository

```bash
git clone <repository-url>
cd intelligent-support-assistant
```

### 2. Configure environment variables

Create a `.env` file or set environment variables:

```bash
export OPENAI_API_KEY=your_openai_api_key
# Or for AWS Bedrock
export AWS_ACCESS_KEY_ID=your_aws_access_key
export AWS_SECRET_ACCESS_KEY=your_aws_secret_key
export AWS_REGION=us-east-1
```

### 3. Run the application

**Development mode (with H2 in-memory database):**

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

**Production mode:**

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

### 4. Access the application

- API Base URL: `http://localhost:8080/api/v1`
- Health Check: `http://localhost:8080/api/v1/health`
- H2 Console (dev only): `http://localhost:8080/h2-console`
- Actuator Endpoints: `http://localhost:8080/actuator`

## API Endpoints

### Query Endpoints

- `POST /api/v1/query` - Submit a query
- `POST /api/v1/query/stream` - Submit query with streaming response

### Document Management

- `POST /api/v1/documents` - Upload a document
- `GET /api/v1/documents/{id}` - Get document details
- `DELETE /api/v1/documents/{id}` - Delete a document

### Health & Monitoring

- `GET /api/v1/health` - Application health check
- `GET /actuator/health` - Detailed health information
- `GET /actuator/metrics` - Application metrics
- `GET /actuator/prometheus` - Prometheus metrics

## Building for Production

```bash
mvn clean package
java -jar target/intelligent-support-assistant-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

## Docker Support

Build Docker image:

```bash
docker build -t intelligent-support-assistant .
```

Run with Docker Compose:

```bash
docker-compose up
```

## Project Structure

```
src/
├── main/
│   ├── java/com/support/assistant/
│   │   ├── config/          # Configuration classes
│   │   ├── controller/      # REST controllers
│   │   ├── model/           # Domain models and DTOs
│   │   │   ├── dto/         # Data Transfer Objects
│   │   │   └── entity/      # JPA entities
│   │   ├── repository/      # Data repositories
│   │   └── service/         # Business logic services
│   └── resources/
│       └── application.yml  # Application configuration
└── test/                    # Test classes
```

## Development Roadmap

This project follows a phased implementation approach:

1. **Phase 1**: Foundation & Basic RAG
2. **Phase 2**: Advanced Retrieval Patterns
3. **Phase 3**: Query Planning & Decomposition
4. **Phase 4**: Multi-Agent Orchestration
5. **Phase 5**: Hallucination Detection & Validation
6. **Phase 6**: Evaluation & Metrics
7. **Phase 7**: Monitoring, Logging & Production Readiness
8. **Phase 8**: Security & Deployment
9. **Phase 9**: Advanced Features & Optimization

## License

[Your License Here]

## Contributing

[Contributing Guidelines]
