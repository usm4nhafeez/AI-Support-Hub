# Project Dependencies

AI Support Hub is built with Spring Boot and Spring AI. The dependency set provides the application with REST API support, security, database persistence, AI model integration, document ingestion, vector search, conversational memory, and development/testing tools.

---

## 1. Spring Boot

### `spring-boot-starter-web`

**Purpose:** REST API and web application support.

Provides Spring MVC, embedded Tomcat, HTTP request handling, and controller support required to expose the application's REST endpoints.

### `spring-boot-starter-actuator`

**Purpose:** Application monitoring and diagnostics.

Provides production-ready endpoints for monitoring application health, metrics, and runtime information.

### `spring-boot-starter-validation`

**Purpose:** Request and data validation.

Provides Jakarta Bean Validation support for constraints such as `@NotNull`, `@Size`, and `@Valid`, helping validate incoming API requests before they reach the business layer.

### `spring-boot-starter-security`

**Purpose:** Application security.

Provides Spring Security's authentication and authorization infrastructure, including security filters and endpoint protection.

### `spring-boot-starter-data-jpa`

**Purpose:** Relational database persistence.

Provides Spring Data JPA and Hibernate integration for mapping Java entities to relational database tables and implementing repository-based data access.

### `spring-boot-starter-jdbc`

**Purpose:** Direct JDBC database access.

Provides Spring's JDBC infrastructure and connection-pooling support for operations that require direct SQL access.

---

## 2. Authentication and JWT

### `jjwt-api`

**Purpose:** JWT API.

Provides the API used by the application to create, parse, and work with JSON Web Tokens.

### `jjwt-impl`

**Purpose:** JWT implementation.

Provides the runtime implementation required by the JJWT API for token creation and verification.

### `jjwt-jackson`

**Purpose:** JWT JSON serialization.

Provides Jackson-based JSON serialization and deserialization support for JWT claims.

Together, these JJWT dependencies provide the foundation for stateless authentication using access tokens.

---

## 3. Oracle Database

### `ojdbc17`

**Purpose:** Oracle JDBC connectivity.

Provides the JDBC driver required for connecting the Spring Boot application to Oracle Database.

The project uses Oracle for relational persistence as well as vector storage for AI retrieval.

---

## 4. Spring AI

### `spring-ai-markdown-document-reader`

**Purpose:** Markdown document ingestion.

Reads Markdown knowledge sources and converts their content into Spring AI `Document` objects that can be processed by the ingestion pipeline.

### `spring-ai-pdf-document-reader`

**Purpose:** PDF document ingestion.

Extracts content from PDF files and converts it into documents that can be processed, split, embedded, and stored for retrieval.

### `spring-ai-starter-model-google-genai`

**Purpose:** Gemini chat model integration.

Connects Spring AI with Google's Gemini models and provides the chat-model infrastructure used to generate natural-language responses.

### `spring-ai-starter-model-google-genai-embedding`

**Purpose:** Text embedding generation.

Converts text into numerical vector representations that can be used for semantic similarity search and retrieval.

### `spring-ai-starter-vector-store-oracle`

**Purpose:** Oracle vector store integration.

Provides Spring AI integration with Oracle's vector capabilities, allowing document embeddings to be stored and searched using vector similarity.

### `spring-ai-starter-model-chat-memory`

**Purpose:** Conversation memory.

Provides the infrastructure required to maintain conversational context between user interactions.

### `spring-ai-starter-model-chat-memory-repository-jdbc`

**Purpose:** Persistent chat memory.

Stores conversation memory through JDBC so conversational context can persist in the database instead of existing only in application memory.

### `spring-ai-vector-store-advisor`

**Purpose:** Retrieval-aware AI workflows.

Provides advisor functionality for incorporating vector-store retrieval into AI interactions, allowing relevant knowledge to be retrieved and supplied to the model when processing a user's request.

---

## 5. Development

### `spring-boot-devtools`

**Purpose:** Faster local development.

Provides development conveniences such as automatic application restarts when source files change.

This dependency is intended for development rather than production deployment.

---

## 6. Testing

### `spring-boot-starter-test`

**Purpose:** Application testing.

Provides the standard Spring Boot testing ecosystem, including JUnit, Mockito, and AssertJ, for testing application components and behavior.

---

## 7. Dependency Management

The project uses the **Spring AI Bill of Materials (BOM)** through Maven dependency management.

The BOM centrally manages compatible Spring AI library versions.

This provides:

* Consistent Spring AI versions across modules
* Reduced dependency version conflicts
* Simplified Maven configuration
* Easier Spring AI upgrades
* Better compatibility between Spring AI components

Application dependencies therefore do not need individual Spring AI version declarations when their versions are managed by the BOM.

---

## Dependency Architecture

The dependencies can be viewed as the following layers:

```text
Spring Boot
    |
    +-- Web / REST API
    +-- Validation
    +-- Security
    +-- JPA / JDBC
    +-- Actuator
    |
    +-- JWT Authentication
    |
    +-- Oracle Database
    |       |
    |       +-- Relational Data
    |       +-- Vector Store
    |
    +-- Spring AI
            |
            +-- Gemini Chat Model
            +-- Gemini Embeddings
            +-- Document Readers
            +-- Vector Store
            +-- Chat Memory
            +-- Retrieval Advisors
```

Together, these dependencies provide the infrastructure required for AI Support Hub's support-agent architecture: secure REST APIs, persistent application data, knowledge ingestion, semantic retrieval, conversational context, and Gemini-powered responses.
