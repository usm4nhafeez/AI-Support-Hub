# AI Support Hub

AI Support Hub is a reusable, multi-tenant customer-support API built with
Spring Boot, Spring AI, Google Gemini, Oracle Database, Oracle Vector Search,
Spring Security, and JWT authentication.

The platform provides:

- Tenant/client management
- Client API-key authentication
- Agent JWT authentication
- Customer conversations and message history
- Retrieval-augmented generation (RAG) over tenant knowledge
- Oracle Vector Search storage
- Optional BookVault tool integration
- Audit logging
- Health and information actuator endpoints

## Technology stack

| Technology | Version |
|---|---|
| Java | 27 |
| Spring Boot | 4.1.1 |
| Spring AI | 2.0.1 |
| Spring Security | Spring Boot managed |
| Google Gemini chat model | `gemini-2.5-flash` |
| Google Gemini embedding model | `gemini-embedding-001` |
| Oracle Database | Oracle Free / `FREEPDB1` |
| Build tool | Maven |

## Architecture

```text
Client Application
        |
        v
REST Controllers
        |
        v
Security Filters
        |
        +--> Client API-key authentication
        +--> Agent JWT authentication
        |
        v
Application Services
        |
        +--> Oracle relational data
        +--> Oracle Vector Search
        +--> Google Gemini
        +--> Optional BookVault tools
```

The customer application only needs to call this REST API. It does not need to
include Spring AI or connect directly to Oracle.

## Requirements

- Java 27
- Maven, or the included Maven wrapper
- Oracle Database with the `FREEPDB1` service
- An Oracle application user such as `AIUSER`
- Google Gemini API key
- Optional BookVault API at `http://localhost:8090/api`

## Configuration

Sensitive values are loaded from environment variables. Do not commit real
passwords, API keys, JWT secrets, or database credentials.

Required environment variables:

```text
GEMINI_API_KEY=<your Gemini API key>
ORACLE_USERNAME=AIUSER
ORACLE_PASSWORD=<your Oracle password>
```

Optional environment variables and their defaults:

```text
JWT_SECRET=change-this-secret-to-a-long-random-value
JWT_EXPIRATION_MS=3600000
DEMO_CLIENT_KEY=bookvault
DEMO_API_KEY=change-me
DEMO_AGENT_USERNAME=admin
DEMO_AGENT_PASSWORD=change-me
BOOKVAULT_URL=http://localhost:8090/api
```

For IntelliJ IDEA, configure these values in:

```text
Run -> Edit Configurations -> Environment variables
```

For a PowerShell session:

```powershell
$env:GEMINI_API_KEY = "your-gemini-api-key"
$env:ORACLE_USERNAME = "AIUSER"
$env:ORACLE_PASSWORD = "your-oracle-password"
$env:DEMO_AGENT_PASSWORD = "your-agent-password"
```

The application connects to:

```text
jdbc:oracle:thin:@//localhost:1521/FREEPDB1
```

For full Oracle user setup, schema creation, existing-schema migration, and
troubleshooting, see [ORACLE_SQL_SETUP.md](ORACLE_SQL_SETUP.md).

For a property-by-property configuration reference, see
[docs/CONFIGURATION.md](docs/CONFIGURATION.md).

## Database setup

Run the Oracle setup guide as follows:

1. Create or configure the `AIUSER` account inside the `FREEPDB1` PDB.
2. Grant the required Oracle privileges and tablespace quota.
3. Run `src/main/resources/sql/schema.sql` as `AIUSER` for a fresh schema.
4. If the schema already exists, inspect it before applying any migration.
5. If an old `ROLE` column remains in `SUPPORT_MESSAGES`, follow the migration
   instructions in [ORACLE_SQL_SETUP.md](ORACLE_SQL_SETUP.md).

The application uses Hibernate `ddl-auto=update` for local development. This is
convenient during development, but controlled migrations are recommended for
production.

The application maps Java boolean values to Oracle numeric boolean values
(`0`/`1`) and maps `Message.role` to the existing `SENDER_TYPE` column.

## Build and run

Build with the Maven wrapper:

```powershell
.\mvnw.cmd clean package
```

Run from IntelliJ IDEA or with Maven:

```powershell
.\mvnw.cmd spring-boot:run
```

The server starts on port `8000`:

```text
http://localhost:8000
```

If port `8000` is already in use on Windows, identify the process:

```powershell
Get-NetTCPConnection -LocalPort 8000 -State Listen
```

Stop only the confirmed old application process or change `server.port`.

## API overview

| Method | Endpoint | Authentication | Purpose |
|---|---|---|---|
| `GET` | `/actuator/health` | Public | Health check |
| `GET` | `/actuator/info` | Public | Application information |
| `POST` | `/api/v1/agent/auth/login` | Public | Agent login and JWT creation |
| `POST` | `/api/v1/clients` | Agent JWT | Create a client |
| `POST` | `/api/v1/knowledge` | Client API key | Ingest tenant knowledge |
| `POST` | `/api/v1/support/chat` | Client API key | Start or continue a conversation |
| `GET` | `/api/v1/agent/conversations` | Agent JWT + client key | List client conversations |

The implemented customer chat route is:

```text
POST /api/v1/support/chat
```

`/api/v1/chat/customer` is not an implemented route.

## Customer chat example

Use the default demo client after startup:

```http
POST http://localhost:8000/api/v1/support/chat
X-Client-Key: bookvault
X-API-Key: change-me
Content-Type: application/json
```

Request body:

```json
{
  "customerId": "user-123",
  "message": "What is the return policy?"
}
```

Example response:

```json
{
  "conversationId": "generated-uuid",
  "status": "AI_HANDLING",
  "response": "..."
}
```

Continue the same conversation by sending the returned ID:

```json
{
  "customerId": "user-123",
  "conversationId": "generated-uuid",
  "message": "Can you explain that in more detail?"
}
```

Customer chat does not require an agent Bearer token. It requires both
`X-Client-Key` and `X-API-Key`.

## Agent login example

```http
POST http://localhost:8000/api/v1/agent/auth/login
Content-Type: application/json
```

```json
{
  "username": "admin",
  "password": "your configured demo agent password"
}
```

Response:

```json
{
  "token": "jwt-token"
}
```

Use the token for agent-protected requests:

```http
Authorization: Bearer <jwt-token>
```

The generated Spring Security password printed at startup is unrelated to the
application's agent login flow.

## Knowledge ingestion

Knowledge is tenant-specific and is embedded into the Oracle vector store:

```http
POST http://localhost:8000/api/v1/knowledge
X-Client-Key: bookvault
X-API-Key: change-me
Content-Type: application/json
```

```json
{
  "name": "Return Policy",
  "category": "policies",
  "version": "1.0",
  "content": "Customers may return eligible books within 30 days of delivery."
}
```

The application also attempts to ingest
`src/main/resources/knowledge/support.txt` during startup.

The current Oracle vector configuration uses top-k retrieval without a
similarity threshold. Oracle requires vector normalization before threshold
filtering can be enabled.

## Postman testing

The complete request-by-request Postman flow is documented in
[TEST.md](TEST.md). It covers:

1. Health check
2. Agent login
3. Optional client creation
4. Knowledge ingestion
5. First customer chat
6. Continuing a conversation
7. Listing conversations as an agent
8. Validation, authentication, and schema error cases

## Troubleshooting

### HTTP 403 on customer chat

Verify that the request uses:

```http
POST /api/v1/support/chat
X-Client-Key: bookvault
X-API-Key: change-me
```

Remove any empty or inherited `Authorization` header. Customer chat does not
use the agent JWT.

### HTTP 403 with an Oracle exception in the log

If the log contains `ORA-01400`, the request passed authentication and failed
while writing to Oracle. Check the database error and compare the live schema
with `src/main/resources/sql/schema.sql`.

### `SUPPORT_MESSAGES.ROLE` or `SUPPORT_MESSAGES.SENDER_TYPE`

Older schemas may contain both columns. Inspect the table and follow the
existing-schema migration in [ORACLE_SQL_SETUP.md](ORACLE_SQL_SETUP.md).

### Vector similarity threshold error

If Oracle reports that similarity threshold filtering requires normalized
vectors, remove the threshold or enable
`spring.ai.vectorstore.oracle.forced-normalization=true` and plan for vector
re-ingestion if the vector schema changes.

### Port 8000 is already in use

Find the listener:

```powershell
Get-NetTCPConnection -LocalPort 8000 -State Listen
```

Stop the confirmed stale Java process or set another value for `server.port`.

## Project documentation

- [TEST.md](TEST.md) - Postman test sequence and request examples
- [ORACLE_SQL_SETUP.md](ORACLE_SQL_SETUP.md) - Oracle account, schema, and migration guide
- [docs/CONFIGURATION.md](docs/CONFIGURATION.md) - Spring Boot configuration reference
- [docs/flow.md](docs/flow.md) - Application flow and security filters
- [docs/AI_Support_Hub_Entity.md](docs/AI_Support_Hub_Entity.md) - Entity overview
- [docs/prd.md](docs/prd.md) - Product requirements

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE).
