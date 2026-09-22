# Application Configuration

AI Support Hub uses Spring Boot configuration properties to define application settings, database connectivity, Gemini AI integration, embeddings, vector storage, and server configuration.

Sensitive credentials are loaded through environment variables rather than being stored directly in the configuration file.

---

## 1. Application

```properties
spring.application.name=ai-support-hub
server.port=8000
```

### `spring.application.name`

Defines the application name used by Spring Boot and supporting infrastructure.

### `server.port`

Runs the application on port `8000`.

The application is therefore available locally at:

```text
http://localhost:8000
```

---

## 2. JPA and Hibernate

```properties
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.OracleDialect
```

### `spring.jpa.hibernate.ddl-auto`

Uses Hibernate's `update` mode to automatically update database tables based on entity mappings during development.

### `spring.jpa.show-sql`

Enables SQL logging to help inspect database operations during development.

### `spring.jpa.properties.hibernate.dialect`

Configures Hibernate to generate SQL appropriate for Oracle Database.

---

## 3. Oracle Database

```properties
spring.datasource.url=jdbc:oracle:thin:@//localhost:1521/FREEPDB1
spring.datasource.username=${ORACLE_USERNAME}
spring.datasource.password=${ORACLE_PASSWORD}
spring.datasource.driver-class-name=oracle.jdbc.OracleDriver
```

### Database URL

Connects the application to the Oracle `FREEPDB1` pluggable database running locally.

### Credentials

The username and password are loaded from environment variables:

```text
ORACLE_USERNAME
ORACLE_PASSWORD
```

This prevents database credentials from being committed to source control.

### JDBC Driver

Uses the Oracle JDBC driver:

```text
oracle.jdbc.OracleDriver
```

---

## 4. Gemini Chat Model

```properties
spring.ai.google.genai.api-key=${GEMINI_API_KEY}
spring.ai.google.genai.chat.options.model=gemini-2.5-flash
```

### API Key

The Gemini API key is loaded from:

```text
GEMINI_API_KEY
```

The key should not be stored directly in `application.properties` or committed to Git.

### Chat Model

The application uses:

```text
gemini-2.5-flash
```

for conversational AI responses.

---

## 5. Gemini Embeddings

```properties
spring.ai.google.genai.embedding.api-key=${GEMINI_API_KEY}
spring.ai.model.embedding.text=google-genai
spring.ai.google.genai.embedding.text.model=gemini-embedding-001
spring.ai.google.genai.embedding.text.task-type=RETRIEVAL_DOCUMENT
```

### Embedding Provider

```text
spring.ai.model.embedding.text=google-genai
```

selects Google GenAI as the text embedding provider.

### Embedding Model

```text
gemini-embedding-001
```

converts document text into vector representations.

### Task Type

```text
RETRIEVAL_DOCUMENT
```

indicates that the generated embeddings are intended for document retrieval.

These embeddings are later stored in the Oracle vector store and compared against query embeddings during semantic search.

---

## 6. Oracle Vector Store

```properties
spring.ai.vectorstore.oracle.initialize-schema=true
spring.ai.vectorstore.oracle.remove-existing-vector-store-table=true
spring.ai.vectorstore.oracle.dimensions=3072
spring.ai.vectorstore.oracle.distance-type=COSINE
spring.ai.vectorstore.oracle.index-type=IVF
```

### Schema Initialization

```text
spring.ai.vectorstore.oracle.initialize-schema=true
```

allows Spring AI to initialize the required Oracle vector-store schema.

### Existing Vector Store Table

```text
spring.ai.vectorstore.oracle.remove-existing-vector-store-table=true
```

allows the existing vector-store table to be removed and recreated during initialization.

**Development warning:** This setting can remove previously stored vector data. It should be disabled or handled carefully when deploying to a persistent environment.

### Vector Dimensions

```text
spring.ai.vectorstore.oracle.dimensions=3072
```

configures the vector dimension expected by the vector store.

This value must match the dimensionality produced by the selected embedding model.

### Distance Metric

```text
spring.ai.vectorstore.oracle.distance-type=COSINE
```

uses cosine similarity to measure the similarity between vectors.

### Index Type

```text
spring.ai.vectorstore.oracle.index-type=IVF
```

configures Oracle's IVF vector index for vector similarity search.

---

## 7. Optional AI System Prompt

```properties
#app.ai.system-prompt=You are a helpful technical assistant. Explain concepts clearly and use simple examples.
```

This property is currently commented out.

It can be enabled when the application introduces a configurable system prompt for controlling the assistant's general behavior.

---

## Environment Variables

Before running the application, configure the following environment variables:

```text
GEMINI_API_KEY
ORACLE_USERNAME
ORACLE_PASSWORD
```

Example PowerShell configuration:

```powershell
$env:GEMINI_API_KEY="your-gemini-api-key"
$env:ORACLE_USERNAME="your-oracle-username"
$env:ORACLE_PASSWORD="your-oracle-password"
```

For production deployments, configure these values through the deployment platform's secret/environment-variable management instead of storing them in source code.

---

## Configuration Flow

```text
Environment Variables
        |
        +--> Gemini API Key
        |       |
        |       +--> Gemini Chat Model
        |       +--> Gemini Embedding Model
        |
        +--> Oracle Credentials
                |
                +--> JPA / Hibernate
                +--> JDBC
                +--> Oracle Vector Store
```

This configuration keeps application behavior in Spring Boot properties while keeping credentials outside the source code.
