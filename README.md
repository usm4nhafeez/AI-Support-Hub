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
- Audit logging
- Built-in agent dashboard UI (chat playground, knowledge training, client onboarding, API guide, three themes, Three.js routing core)
- Rate limiting on login and chat endpoints
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
```

The customer application only needs to call this REST API. It does not need to
include Spring AI or connect directly to Oracle.

## Requirements

- Java 27
- Maven, or the included Maven wrapper
- Oracle Database with the `FREEPDB1` service
- An Oracle application user such as `AIUSER`
- Google Gemini API key

## Configuration

Sensitive values are loaded from environment variables. Do not commit real
passwords, API keys, JWT secrets, or database credentials.

Required environment variables:

```text
GEMINI_API_KEY=<required Gemini API key>
ORACLE_USERNAME=<required Oracle username>
ORACLE_PASSWORD=<required Oracle password>
JWT_SECRET=<required random secret, at least 32 bytes>
```

Optional environment variables and their defaults:

```text
JWT_EXPIRATION_MS=3600000
DEMO_SEED_ENABLED=true
DEMO_CLIENT_KEY=demo-client
DEMO_API_KEY=            (empty by default)
DEMO_AGENT_USERNAME=admin
DEMO_AGENT_PASSWORD=     (empty by default)
```

Demo seeding is conditional: if `DEMO_API_KEY` or `DEMO_AGENT_PASSWORD` is
unset (or left as a `change-me` placeholder), the application logs a warning and
skips creating that demo row — placeholder secrets are never stored. Set
`DEMO_SEED_ENABLED=false` to disable demo seeding entirely. Register real
clients from the dashboard's **Clients** screen instead.

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
| `POST` | `/api/v1/agent/auth/login` | Public (rate-limited: 10/min per IP) | Agent login and JWT creation |
| `GET` | `/api/v1/clients` | Agent JWT | List clients |
| `POST` | `/api/v1/clients` | Agent JWT | Create a client |
| `POST` | `/api/v1/clients/{id}/rotate-api-key` | Agent JWT | Issue a new API secret for an existing project (id, client key and trained knowledge are kept; new key returned once) |
| `GET` | `/api/v1/clients/{id}/agents` | Agent JWT | List a project's support agents (username, role, active, created date — no secrets) |
| `POST` | `/api/v1/clients/{id}/agents` | Agent JWT | Add a support-agent login for a project (`{username, password}`; BCrypt-hashed, taken usernames get a `-xxxx` variant — response username is authoritative) |
| `DELETE` | `/api/v1/clients/{id}/agents/{agentId}` | Agent JWT | Remove one agent login; their tickets return to the queue and the username frees up (blocked for your own account and a project's last admin) |
| `GET` | `/api/v1/agent/team` | Agent JWT | List the signed-in agent's own teammates (feeds the assign dropdown) |
| `POST` | `/api/v1/agent/conversations/{id}/assign` | Agent JWT | Assign a conversation to a teammate (`{username}`); empty body assigns it to the signed-in agent |
| `DELETE` | `/api/v1/agent/conversations/{id}/assign` | Agent JWT | Release an assigned conversation back to the queue |
| `POST` | `/api/v1/knowledge` | Client API key | Ingest tenant knowledge |
| `POST` | `/api/v1/knowledge/file` | Client API key | Ingest a PDF or Markdown file |
| `GET` | `/api/v1/clients/{id}/knowledge` | Agent JWT | List a project's knowledge documents |
| `POST` | `/api/v1/clients/{id}/knowledge` | Agent JWT | Ingest knowledge for a project without its API key |
| `POST` | `/api/v1/clients/{id}/knowledge/file` | Agent JWT | Upload a PDF/Markdown for a project |
| `POST` | `/api/v1/support/chat` | Client API key (rate-limited: 30/min per client) | Start or continue a conversation |
| `GET` | `/api/v1/agent/conversations` | Agent JWT | List client conversations |

Every error response uses the same JSON shape:
`{timestamp, status, error, code, message, path}`. Unknown routes return `404`;
rate limits return `429` with a `Retry-After` header.

## Agent dashboard

A single-page dashboard is served at the application root:

```text
http://localhost:8000
```

Log in with the demo agent credentials (when seeded) or any registered agent.
The dashboard provides:

- **Conversations** — recent tenant conversations.
- **Chat Playground** — test the bot with a client's key pair.
- **Knowledge** — pick any project and train it with text, PDF, or Markdown (uses agent JWT; no client API key needed).
- **Clients** — onboard a project; the client key and API key are generated automatically and the API key is shown once. The support-agent roster lives here too: add or remove teammate logins, while `admin` rows show a **Protected** note instead of a Remove button, so the dashboard never offers a removal the API would refuse.
- **API Guide** — copy-paste integration documentation for embedding the bot in other projects.
- **About** — who built the hub and where to reach them, plus the stack it runs on.

Three themes ship with the console — **Console** (dark), **Daylight** (light) and **Blueprint**
(navy with an engineering grid) — picked from the sign-in screen or the top bar and remembered per
browser. Each one also recolours the Three.js *routing core*: a wireframe AI core with tenant
stations on an orbit and packets running the wires in and back out. It sits behind the sign-in card
and as an instrument-sized copy in the sidebar, pauses when the tab is hidden or you are signed out,
holds a still frame for `prefers-reduced-motion`, and falls back to a flat CSS backdrop where WebGL
is unavailable. Three.js r180 is vendored under `src/main/resources/static/assets/vendor/`, so no
CDN is needed.

Every view is deep-linked (`http://localhost:8000/#clients`, `/#about`, …), so a reload or a shared
link opens the same screen.

For embedding the bot in another application, see
[docs/API_INTEGRATION.md](docs/API_INTEGRATION.md).

The implemented customer chat route is:

```text
POST /api/v1/support/chat
```

`/api/v1/chat/customer` is not an implemented route.

## Customer chat example

Use the configured demo client after startup:

```http
POST http://localhost:8000/api/v1/support/chat
X-Client-Key: demo-client
X-API-Key: your configured demo API key
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
X-Client-Key: demo-client
X-API-Key: your configured demo API key
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

PDF and Markdown files can be ingested without copying their text into JSON:

```http
POST http://localhost:8000/api/v1/knowledge/file
X-Client-Key: demo-client
X-API-Key: your configured demo API key
Content-Type: multipart/form-data
```

Use a multipart field named `file`. Optional form fields are `name`, `category`,
and `version`. Files must use a `.pdf`, `.md`, or `.markdown` extension.

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
X-Client-Key: demo-client
X-API-Key: your configured demo API key
```

Remove any empty or inherited `Authorization` header. Customer chat does not
use the agent JWT.

### HTTP 403 with an Oracle exception in the log

If the log contains `ORA-01400`, the request passed authentication and failed
while writing to Oracle. Check the database error and compare the live schema
with `src/main/resources/sql/schema.sql`.

### HTTP 429 (rate limited)

Login is limited to 10 requests/minute per IP and chat to 30 requests/minute
per client key. Wait for the number of seconds in the `Retry-After` response
header and retry.

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
- [docs/API_INTEGRATION.md](docs/API_INTEGRATION.md) - Embedding the bot in another project (keys, chat, knowledge, errors, limits)
- [ORACLE_SQL_SETUP.md](ORACLE_SQL_SETUP.md) - Oracle account, schema, and migration guide
- [docs/CONFIGURATION.md](docs/CONFIGURATION.md) - Spring Boot configuration reference
- [docs/DEPENDENCIES.md](docs/DEPENDENCIES.md) - Dependency-by-dependency reference
- [docs/flow.md](docs/flow.md) - Application flow and security filters
- [docs/AI_Support_Hub_Entity.md](docs/AI_Support_Hub_Entity.md) - Entity overview
- [docs/prd.md](docs/prd.md) - Product requirements

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE).
