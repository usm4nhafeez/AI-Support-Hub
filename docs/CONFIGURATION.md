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
#spring.jpa.show-sql=false
#spring.jpa.properties.hibernate.format_sql=false
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.OracleDialect
```

### `spring.jpa.hibernate.ddl-auto`

Uses Hibernate's `update` mode to automatically update database tables based on entity mappings during development.

### `spring.jpa.show-sql`

Enables SQL logging to help inspect database operations during development.

### `spring.jpa.properties.hibernate.format_sql`

Formats logged SQL across multiple lines so it is easier to read while debugging.

### `spring.jpa.properties.hibernate.dialect`

Configures Hibernate to generate SQL appropriate for Oracle Database.

### Recommended usage for these JPA properties

- `spring.jpa.hibernate.ddl-auto=update`: convenient in development because schema changes are auto-applied, but avoid in production where controlled migrations are safer.
- `spring.jpa.show-sql=false`: SQL logging is disabled by default; enable it temporarily when debugging.
- `spring.jpa.properties.hibernate.format_sql=false`: SQL formatting is disabled by default and can be enabled with SQL logging.
- `spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.OracleDialect`: required when using Oracle so Hibernate generates Oracle-compatible SQL.

---

## 3. Oracle Database

```properties
spring.datasource.url=${ORACLE_URL:jdbc:oracle:thin:@//localhost:1521/FREEPDB1}
spring.datasource.username=${ORACLE_USERNAME}
spring.datasource.password=${ORACLE_PASSWORD}
spring.datasource.driver-class-name=oracle.jdbc.OracleDriver
```

### Database URL

By default the application connects to the Oracle `FREEPDB1` pluggable database
running on this machine. Set `ORACLE_URL` to point somewhere else — a remote
database, or your own machine from inside a Docker container, where `localhost`
means the container and not the host:

```text
ORACLE_URL=jdbc:oracle:thin:@//host.docker.internal:1521/FREEPDB1
```

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
spring.ai.vectorstore.oracle.remove-existing-vector-store-table=false
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
spring.ai.vectorstore.oracle.remove-existing-vector-store-table=false
```

preserves the existing vector-store table and embeddings during normal startup.

Set this to `true` only when intentionally rebuilding the vector-store table.
That operation deletes existing vector data.

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
COSINE (means cosine distance).

### Index Type

```text
spring.ai.vectorstore.oracle.index-type=IVF
```

configures Oracle's IVF vector index for vector similarity search.
IVF - Inverted File Index.

### Similarity threshold and normalization

The application currently uses top-k retrieval without a similarity threshold.
Oracle requires normalized vectors before threshold filtering can be used. If
threshold filtering is enabled later, configure:

```properties
spring.ai.vectorstore.oracle.forced-normalization=true
```

Changing vector dimensions or normalization settings may require rebuilding the
vector-store table and re-ingesting knowledge documents.

---

## 7. Optional AI System Prompt

```properties
#app.ai.system-prompt=You are a helpful technical assistant. Explain concepts clearly and use simple examples.
```

This property is currently commented out.

It can be enabled when the application introduces a configurable system prompt for controlling the assistant's general behavior.

---

## 8. JWT Authentication

```properties
support.security.jwt-secret=${JWT_SECRET}
support.security.jwt-expiration-ms=${JWT_EXPIRATION_MS:3600000}
```

### `JWT_SECRET`

Required signing secret for agent tokens. Must contain at least 32 random bytes.

### `JWT_EXPIRATION_MS`

Token lifetime in milliseconds; defaults to `3600000` (1 hour).

---

## 9. Demo Seeding

```properties
support.demo.enabled=${DEMO_SEED_ENABLED:true}
support.demo.client-key=${DEMO_CLIENT_KEY:demo-client}
support.demo.api-key=${DEMO_API_KEY:}
support.demo.agent-username=${DEMO_AGENT_USERNAME:admin}
support.demo.agent-password=${DEMO_AGENT_PASSWORD:}
```

`DemoDataInitializer` runs at startup and creates the demo client, demo agent,
and starter knowledge.

### Placeholder-safe behavior

`DEMO_API_KEY` and `DEMO_AGENT_PASSWORD` have **empty defaults**. If either is
unset or set to `change-me`, the application logs a warning and skips that demo
row — placeholder secrets are never stored in the database. Register real
clients from the dashboard instead.

### `DEMO_SEED_ENABLED`

Set to `false` to skip demo seeding entirely (recommended for shared or
production environments).

---

## 10. Rate Limiting

Rate limits are implemented in `RateLimitFilter` and are currently fixed in
code, not configurable through properties:

```text
POST /api/v1/agent/auth/login   10 requests/minute per IP
POST /api/v1/support/chat       30 requests/minute per X-Client-Key (falls back to IP)
```

Exceeding a limit returns HTTP `429` with a `Retry-After` header.

---

## 11. File Upload Limits

Knowledge file ingestion (`/api/v1/knowledge/file` and the agent variant)
accepts multipart uploads up to a configured size:

```properties
spring.servlet.multipart.max-file-size=25MB
spring.servlet.multipart.max-request-size=30MB
```

Spring's built-in default is 1 MB per file, which caused the server to abort
oversize uploads mid-request (the browser reported "Failed to fetch"). With the
explicit limits above, a file over 25 MB gets a clean `413` `UPLOAD_TOO_LARGE`
JSON error instead.

---

## Environment Variables

Before running the application, configure these required environment variables:

```text
GEMINI_API_KEY
ORACLE_USERNAME
ORACLE_PASSWORD
JWT_SECRET
```

`JWT_SECRET` must contain at least 32 random bytes. Generate one with:

```powershell
node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"
```

The following demo variables are optional. `DEMO_API_KEY` and
`DEMO_AGENT_PASSWORD` default to empty; when unset, the matching demo rows are
skipped with a warning rather than seeded with placeholder secrets.

```text
ORACLE_URL               (default jdbc:oracle:thin:@//localhost:1521/FREEPDB1)
JWT_EXPIRATION_MS        (default 3600000)
DEMO_SEED_ENABLED        (default true; false disables demo seeding)
DEMO_CLIENT_KEY          (default demo-client)
DEMO_API_KEY             (default empty — skip seeding the demo client)
DEMO_AGENT_USERNAME      (default admin)
DEMO_AGENT_PASSWORD      (default empty — skip seeding the demo agent)
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
