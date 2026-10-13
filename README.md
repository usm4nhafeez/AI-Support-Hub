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
        +--> Oracle Vector Search   (or PostgreSQL + pgvector, see below)
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

PostgreSQL is an alternative to Oracle for everything above. Install the
`vector` extension instead, activate the `pgvector` profile, and no Oracle
database is needed. Oracle stays the default and nothing about it changes; see
[Choosing your database](#choosing-your-database-oracle-or-postgresql--pgvector).

## Configuration

Sensitive values are loaded from environment variables. Do not commit real
passwords, API keys, JWT secrets, or database credentials.

Required environment variables:

```text
GEMINI_API_KEY=<required Gemini API key>
ORACLE_URL=<required JDBC URL, e.g. jdbc:oracle:thin:@//localhost:1521/FREEPDB1>
ORACLE_USERNAME=<required Oracle username>
ORACLE_PASSWORD=<required Oracle password>
JWT_SECRET=<required random secret, at least 32 bytes>
```

The database URL has no built-in default: it comes from the environment so one
build works on any machine. On the `pgvector` profile the three `ORACLE_*`
variables are replaced by `SUPABASE_DB_URL`, `DB_USERNAME` and `DB_PASSWORD`,
and `ORACLE_URL` is not read at all.

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

To run the same application as a Docker container instead of from your IDE, see
[docs/DOCKER.md](docs/DOCKER.md). A container sees its own `localhost`, so it needs its own
`ORACLE_URL` pointing at `host.docker.internal`.

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

## Choosing your database: Oracle or PostgreSQL + pgvector

Oracle is the default and stays fully supported: run the application exactly as
before and it uses `ORACLE_URL`, the Oracle dialect and Oracle Vector Search. No
Oracle setting moved into a profile, and nothing about that path changes unless
you ask for the `pgvector` profile.

The profile is switched on, never mixed in:

```text
SPRING_PROFILES_ACTIVE=pgvector
```

IntelliJ IDEA: Run -> Edit Configurations -> *ai hub* -> Active profiles: `pgvector`.

Maven or a jar from a terminal:

```powershell
$env:SPRING_PROFILES_ACTIVE = "pgvector"
$env:SUPABASE_DB_URL = "jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres?sslmode=require"
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "your-database-password"
.\mvnw.cmd spring-boot:run
```

```bash
SPRING_PROFILES_ACTIVE=pgvector java -jar target/ai-support-hub-0.0.1-SNAPSHOT.jar
```

Docker, reusing the same image:

```text
docker run --rm -p 8001:8000 --env-file .env.pgvector ai-support-hub:0.0.1
```

### What the profile changes

| Concern | Default (Oracle) | `pgvector` profile |
| --- | --- | --- |
| JDBC URL, user, password | `ORACLE_URL`, `ORACLE_USERNAME`, `ORACLE_PASSWORD` | `SUPABASE_DB_URL`, `DB_USERNAME`, `DB_PASSWORD` |
| Driver and dialect | `oracle.jdbc.OracleDriver`, `OracleDialect` | `org.postgresql.Driver`, `PostgreSQLDialect` |
| Vector store | Oracle Vector Search, 3072-wide embeddings | pgvector table `public.vector_store`, 1536-wide embeddings |
| Auto-configuration | pgvector store switched off | Oracle store switched off |
| Startup SQL | none | `sql/postgres-bootstrap.sql`: the `vector`, `hstore` and `uuid-ossp` extensions plus a `clob` domain over `text` |

Two details worth knowing before you switch:

**Vectors do not transfer.** The two stores index different widths, so knowledge
documents must be trained again against whichever database a project points at.
Relational rows do not transfer either; a fresh Postgres database starts empty
and demo seeding and the Clients screen create what you need.

**The long-text columns need the `clob` domain.** Entities declare
`columnDefinition = "CLOB"`, which is raw DDL that Hibernate sends to whichever
database it is on. PostgreSQL has no `CLOB` type, so the bootstrap script
defines a domain of that name over `text`. That keeps the Oracle mapping
completely untouched instead of editing shared entities, and it runs before
Hibernate creates the tables.

### A local PostgreSQL with pgvector

The quickest way to get a database to try, using the Docker setup from
[docs/DOCKER.md](docs/DOCKER.md):

```powershell
docker run -d --name ash-pgvector -e POSTGRES_PASSWORD=your-postgres-password -e POSTGRES_DB=ash -v ash-pg-data:/var/lib/postgresql/data -p 5433:5432 pgvector/pgvector:pg17
```

Port 5433 keeps it clear of any PostgreSQL already on 5432, and the named volume
keeps the data across container restarts. The application then reads:

```text
SUPABASE_DB_URL=jdbc:postgresql://localhost:5433/ash
DB_USERNAME=postgres
DB_PASSWORD=your-postgres-password
```

`POSTGRES_PASSWORD` above belongs to the container image; `DB_PASSWORD` is the
name the application reads. They hold the same value.

The quickest way to check you got the store you asked for:

```powershell
docker exec ash-pgvector psql -U postgres -d ash -c "\d vector_store"
```

The `embedding` column reads `vector(1536)` and the index is
`hnsw (embedding vector_cosine_ops)`. Delete the container with
`docker rm -f ash-pgvector` when you are done; the volume keeps the data if you
want to start it again later.

### Supabase (hosted pgvector, no card)

This is the profile's main reason to exist: the database no longer has to run on
your machine, so the app can be deployed somewhere free. Create a project, then
in the dashboard open Project Settings -> Database and copy the **Session**
connection string. Build the URL as:

```text
SUPABASE_DB_URL=jdbc:postgresql://<aws-host>.pooler.supabase.com:5432/postgres?sslmode=require
```

Three things to get right:

Use the **Session** pooler (port 5432), not the Transaction pooler (6543). A
Spring Boot app holds a small pool of real connections through HikariCP, and
transaction pooling discards session state between them. Keep `sslmode=require`:
Supabase refuses plain connections. Hibernate's `ddl-auto=update` will create all
twelve `support_*` tables plus `vector_store` on first boot, which is fine for a
scratch project and a poor idea for a production one.

`initialize-schema=true` makes Spring AI run `CREATE EXTENSION IF NOT EXISTS
vector` itself. If the connection role is not allowed to do that, enable pgvector
once in the dashboard under Database -> Extensions, and the bootstrap script and
the store both become no-ops on subsequent boots.

Free tiers on Supabase, Neon and similar hosts need no card, which is what makes
this profile the difference between "runs on my laptop" and "runs somewhere
public". The trade is that a free project sleeps when idle, so the first request
after a quiet period waits for the database to wake.

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
- [docs/DOCKER.md](docs/DOCKER.md) - Build, run, stop and re-run the project as a Docker container
- [docs/DEPENDENCIES.md](docs/DEPENDENCIES.md) - Dependency-by-dependency reference
- [docs/flow.md](docs/flow.md) - Application flow and security filters
- [docs/AI_Support_Hub_Entity.md](docs/AI_Support_Hub_Entity.md) - Entity overview
- [docs/prd.md](docs/prd.md) - Product requirements

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE).
