# Project Dependencies

This project uses Spring Boot as the application foundation and Spring AI for AI-powered document and chat features. The dependency set below explains what each library does and why it is included.

## 1. Spring Boot Core Dependencies

### `org.springframework.boot:spring-boot-starter-actuator`
- What it is: Spring Boot monitoring and management starter.
- Why it is used: Exposes production-ready endpoints such as health, metrics, and application status for monitoring and diagnostics.

### `org.springframework.boot:spring-boot-starter-web`
- What it is: The standard Spring Boot web starter.
- Why it is used: Provides the Spring MVC stack, embedded servlet container, and REST API support needed for controller-based APIs.

### `org.springframework.boot:spring-boot-starter-validation`
- What it is: Validation support for Java Bean constraints.
- Why it is used: Enables annotations like `@Valid`, `@NotNull`, and `@Size` for request payload validation and safer API inputs.

### `org.springframework.boot:spring-boot-starter-security`
- What it is: Spring Security starter.
- Why it is used: Adds authentication and authorization controls, security filters, and protection for application endpoints.

### `org.springframework.boot:spring-boot-starter-data-jpa`
- What it is: JPA support for Spring Data.
- Why it is used: Simplifies database access with entity mappings, repositories, and transactions for relational data persistence.

### `org.springframework.boot:spring-boot-starter-jdbc`
- What it is: JDBC support starter.
- Why it is used: Provides the JDBC infrastructure needed for direct SQL database access and connection pooling support.

## 2. Authentication and JWT

### `io.jsonwebtoken:jjwt-api`
- What it is: JJWT API library.
- Why it is used: Provides the interfaces and classes for generating and parsing JWT tokens.

### `io.jsonwebtoken:jjwt-impl`
- What it is: JJWT implementation library.
- Why it is used: Supplies the actual JWT encoding and decoding logic at runtime.

### `io.jsonwebtoken:jjwt-jackson`
- What it is: Jackson serializer/deserializer support for JJWT.
- Why it is used: Allows JWT claims and payloads to be serialized/deserialized using Jackson, which is already part of the Spring Boot stack.

## 3. Database and Oracle Driver

### `com.oracle.database.jdbc:ojdbc17`
- What it is: Oracle JDBC driver.
- Why it is used: Enables the application to connect to an Oracle database, which matches the Oracle vector store and persistence requirements of this project.

## 4. Spring AI and Document Intelligence

### `org.springframework.ai:spring-ai-markdown-document-reader`
- What it is: Reader for Markdown documents.
- Why it is used: Lets the AI pipeline ingest Markdown content from knowledge sources and convert it into documents for processing.

### `org.springframework.ai:spring-ai-pdf-document-reader`
- What it is: Reader for PDF documents.
- Why it is used: Enables the project to extract text from PDFs and feed it into AI workflows for retrieval and reasoning.

### `org.springframework.ai:spring-ai-starter-model-google-genai`
- What it is: Google Gemini chat model starter.
- Why it is used: Connects the application to Google’s GenAI model for conversational AI features and natural-language responses.

### `org.springframework.ai:spring-ai-starter-model-google-genai-embedding`
- What it is: Embedding model starter for Google GenAI.
- Why it is used: Creates vector embeddings from text so the system can perform semantic search and retrieval.

### `org.springframework.ai:spring-ai-starter-vector-store-oracle`
- What it is: Oracle vector store integration.
- Why it is used: Stores embeddings in an Oracle database and supports vector similarity search for AI retrieval workflows.

### `org.springframework.ai:spring-ai-starter-model-chat-memory`
- What it is: Memory support for chat conversations.
- Why it is used: Keeps context across interactions so the AI model can remember previous conversation turns more effectively.

### `org.springframework.ai:spring-ai-starter-model-chat-memory-repository-jdbc`
- What it is: JDBC-backed chat memory repository.
- Why it is used: Persists conversational memory in a database so chat context is retained across application restarts.

### `org.springframework.ai:spring-ai-vector-store-advisor`
- What it is: Advisor utility for vector stores.
- Why it is used: Helps optimize vector search, retrieval logic, and advisor-driven AI workflows.

## 5. Development Tooling

### `org.springframework.boot:spring-boot-devtools`
- What it is: Spring Boot developer tools.
- Why it is used: Improves local development with automatic restarts, live reload behavior, and faster iteration cycles.

## 6. Testing

### `org.springframework.boot:spring-boot-starter-test`
- What it is: Standard Spring Boot test starter.
- Why it is used: Provides essential testing libraries such as JUnit, Mockito, and AssertJ to validate application behavior reliably.

## Dependency Management

The project uses a Spring AI BOM in the `<dependencyManagement>` section so the correct Spring AI compatible versions are managed centrally.

This helps:
- keep all Spring AI libraries version-aligned,
- avoid version mismatch errors,
- simplify upgrades when the platform evolves.
