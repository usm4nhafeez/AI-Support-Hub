# AI Support Hub - Postman Testing Flow

## Base URL

```text
http://localhost:8000
```

Create a Postman environment with these variables:

```text
baseUrl = http://localhost:8000
clientKey = demo-client
apiKey = <value configured in DEMO_API_KEY>
agentUsername = admin
agentPassword = <value configured in IntelliJ>
agentToken =
conversationId =
clientId =
```

Demo seeding is conditional: the demo client and agent are only created when
`DEMO_API_KEY` and `DEMO_AGENT_PASSWORD` are set to real values at startup
(placeholders like `change-me` are skipped with a warning). If they were not
seeded, register a client through the dashboard (Clients screen) or step 3 and
use the generated keys for `clientKey`/`apiKey`.

The customer chat endpoint is:

```text
POST {{baseUrl}}/api/v1/support/chat
```

Do not use `/api/v1/chat/customer`; that endpoint is not implemented.

## 1. Check application health

### Request

```http
GET {{baseUrl}}/actuator/health
```

No headers are required.

### Expected response

```json
{
  "status": "UP"
}
```

Note: the health endpoint can report `503` when a downstream component (for
example the vector store check) is degraded even though the API itself works.
If chat requests succeed, treat the app as healthy.

## 2. Login as an agent

The demo agent is created automatically when the application starts, provided
`DEMO_AGENT_PASSWORD` was set to a real value (see Base URL note above). Login
is rate-limited to 10 requests per minute per IP.

### Request

```http
POST {{baseUrl}}/api/v1/agent/auth/login
Content-Type: application/json
```

Body:

```json
{
  "username": "{{agentUsername}}",
  "password": "{{agentPassword}}"
}
```

### Expected response

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```

Add this script in the Postman **Tests** tab to save the token:

```javascript
const json = pm.response.json();
pm.environment.set("agentToken", json.token);
```

If login returns `400` or `401`, verify the `support.demo.agent-password` value in IntelliJ's environment variables.

## 3. Create another client

This endpoint requires an agent JWT. This step is optional because the application automatically creates the configured demo client during startup.

### Request

```http
POST {{baseUrl}}/api/v1/clients
Authorization: Bearer {{agentToken}}
Content-Type: application/json
```

Body:

```json
{
  "clientKey": "demo-client",
  "name": "Demo Client",
  "apiKey": "demo-api-key"
}
```

### Expected response

```json
{
  "id": 2,
  "clientKey": "demo-client",
  "name": "Demo Client",
  "active": true,
  "createdAt": "...",
  "updatedAt": "..."
}
```

## 4. Ingest knowledge for the client

Use the client API-key authentication headers.

### Request

```http
POST {{baseUrl}}/api/v1/knowledge
X-Client-Key: {{clientKey}}
X-API-Key: {{apiKey}}
Content-Type: application/json
```

Body:

```json
{
  "name": "Return Policy",
  "category": "policies",
  "version": "1.0",
  "content": "Customers may return eligible books within 30 days of delivery. Items must be unused and in their original condition. Refunds are issued after the returned item is inspected."
}
```

### Expected response

```json
{
  "id": 1,
  "name": "Return Policy",
  "category": "policies",
  "version": "1.0",
  "createdAt": "..."
}
```

This request stores knowledge metadata in Oracle and creates vector embeddings in the Oracle vector store.

The application also attempts to ingest `src/main/resources/knowledge/support.txt` automatically during startup.

The current Oracle vector configuration uses top-k retrieval without a similarity
threshold. This avoids Oracle's requirement that all vectors be normalized before
threshold filtering is allowed.

## 5. Start a customer chat

### Request

```http
POST {{baseUrl}}/api/v1/support/chat
X-Client-Key: {{clientKey}}
X-API-Key: {{apiKey}}
Content-Type: application/json
```

Body:

```json
{
  "customerId": "user-123",
  "message": "What is the return policy?"
}
```

### Expected response

```json
{
  "conversationId": "generated-uuid",
  "status": "AI_HANDLING",
  "response": "Customers may return eligible books within 30 days..."
}
```

Add this script in the Postman **Tests** tab to save the conversation ID:

```javascript
const json = pm.response.json();
pm.environment.set("conversationId", json.conversationId);
```

### Internal flow

```text
API-key filter
    -> find client
    -> find or create customer
    -> create conversation
    -> save customer message
    -> search client-specific knowledge
    -> call Gemini
    -> save AI response
    -> write audit event
    -> return ChatResponse
```

## 6. Continue the same conversation

### Request

```http
POST {{baseUrl}}/api/v1/support/chat
X-Client-Key: {{clientKey}}
X-API-Key: {{apiKey}}
Content-Type: application/json
```

Body:

```json
{
  "customerId": "user-123",
  "conversationId": "{{conversationId}}",
  "message": "Can I get help from a human?"
}
```

The `conversationId` must belong to the same client. Otherwise the service returns `Conversation not found`.

## 7. List conversations as an agent

This endpoint requires an agent JWT. The tenant is taken from the signed token.

### Request

```http
GET {{baseUrl}}/api/v1/agent/conversations
Authorization: Bearer {{agentToken}}
```

### Expected response

```json
[
  {
    "id": 1,
    "conversationKey": "...",
    "status": "AI_HANDLING",
    "createdAt": "...",
    "updatedAt": "..."
  }
]
```

## 8. List clients as an agent

### Request

```http
GET {{baseUrl}}/api/v1/clients
Authorization: Bearer {{agentToken}}
```

### Expected response

A JSON array of `ClientResponse` objects (`id`, `clientKey`, `name`, `active`,
timestamps). No API key or hash is ever included. Save a `clientId` from the
response for step 9.

## 9. Train a project as an agent (no client API key)

The dashboard Knowledge screen uses these routes to train any project with only
a JWT.

### List documents

```http
GET {{baseUrl}}/api/v1/clients/{{clientId}}/knowledge
Authorization: Bearer {{agentToken}}
```

### Ingest text

```http
POST {{baseUrl}}/api/v1/clients/{{clientId}}/knowledge
Authorization: Bearer {{agentToken}}
Content-Type: application/json
```

Body (same shape as step 4):

```json
{ "name": "Shipping FAQ", "category": "faq", "version": "1.0", "content": "Shipping takes 2-4 business days." }
```

### Upload a file

```http
POST {{baseUrl}}/api/v1/clients/{{clientId}}/knowledge/file
Authorization: Bearer {{agentToken}}
Content-Type: multipart/form-data
```

Form fields: `file` (required, `.pdf`/`.md`/`.markdown`), plus optional `name`,
`category`, `version`. Other extensions return `400`.

The same client-API-key variants exist for both ingestion styles:

```http
POST {{baseUrl}}/api/v1/knowledge/file
X-Client-Key: {{clientKey}}
X-API-Key: {{apiKey}}
```

## 10. Dashboard UI

Open `http://localhost:8000` in a browser.

1. Log in with the agent credentials.
2. Themes: switch between **Console**, **Daylight** and **Blueprint** — on the
   sign-in screen and in the top bar. The choice persists across reloads
   (`ash.theme` in `localStorage`), recolours the Three.js routing core, and
   updates the `theme-color` meta tag.
3. Routing core: the constellation animates behind the sign-in card, then stops
   there and starts again in the sidebar once signed in, and pulses when an AI
   reply lands. With `prefers-reduced-motion` it renders one still frame;
   without WebGL the flat CSS backdrop stands in and nothing else changes.
4. Deep links: open `/#knowledge`, or click a view and reload — the same screen
   comes back.
5. Knowledge screen: pick a project from the dropdown and ingest text — the new
   document chip list refreshes.
6. Clients screen: onboard a project, copy the generated key pair (API key shown
   once), rotate a key, add and remove an agent. In the Support agents list,
   `admin`-role rows show a **Protected** note instead of a Remove button (the
   dashboard never offers a removal the API would refuse with `409`), and your own
   row shows a disabled **Signed in**.
7. Chat Playground: chat with the new keys.
8. API Guide: verify the curl examples and "Copy with my credentials".
9. About: the developer card lists GitHub, LinkedIn and email; the name, bio and
   photo come from the public GitHub profile when it is reachable and fall back
   to baked values offline. That request carries no hub credentials.

## Authentication matrix

| Endpoint | Authentication |
|---|---|
| `GET /actuator/health` | Public |
| `GET /actuator/info` | Public |
| `GET /` and static assets | Public (dashboard SPA) |
| `POST /api/v1/agent/auth/login` | Public (rate-limited) |
| `POST /api/v1/support/chat` | `X-Client-Key` + `X-API-Key` (rate-limited) |
| `POST /api/v1/knowledge` | `X-Client-Key` + `X-API-Key` |
| `POST /api/v1/knowledge/file` | `X-Client-Key` + `X-API-Key` |
| `GET /api/v1/agent/conversations` | `Authorization: Bearer ...` |
| `GET`/`POST /api/v1/clients` | `Authorization: Bearer ...` |
| `GET`/`POST /api/v1/clients/{id}/knowledge[/file]` | `Authorization: Bearer ...` |

## Important Postman details

Customer requests require these exact headers:

```http
X-Client-Key: {{clientKey}}
X-API-Key: {{apiKey}}
Content-Type: application/json
```

Do not use only a Bearer token for customer chat. Customer endpoints authenticate through the API-key filter.

Agent requests require:

```http
Authorization: Bearer {{agentToken}}
```

The generated Spring Security password shown in the startup log is unrelated to the application's agent login. Use the configured demo agent username and password.

## Validation and error tests

### Missing API-key headers

Call the customer chat endpoint without `X-Client-Key` and `X-API-Key`:

```http
POST {{baseUrl}}/api/v1/support/chat
```

Expected:

```text
403 Forbidden
```

### Wrong URL

This endpoint is not implemented:

```http
POST {{baseUrl}}/api/v1/chat/customer
```

Use:

```http
POST {{baseUrl}}/api/v1/support/chat
```

### Unknown route (404)

With a valid agent JWT, request a path that does not exist:

```http
GET {{baseUrl}}/api/v1/does-not-exist
Authorization: Bearer {{agentToken}}
```

Expected: HTTP `404` with `{"code":"NOT_FOUND","message":"No endpoint or resource matches this path.", ...}`.
Unauthenticated requests to unknown API routes return `403` (the security layer
does not disclose which routes exist).

### Rate limit (429)

Call `POST /api/v1/agent/auth/login` 11 times rapidly (wrong password is fine):

```text
Requests 1-10: 401 INVALID_CREDENTIALS
Request 11:    429 with a Retry-After header
```

The `429` body has `"code":"RATE_LIMITED"`. Chat is limited similarly at
30 requests/minute per `X-Client-Key`.

### Invalid request body

```json
{
  "customerId": "",
  "message": ""
}
```

Expected: HTTP `400` validation failure.

### Invalid conversation ID

```json
{
  "customerId": "user-123",
  "conversationId": "invalid-id",
  "message": "Continue our conversation"
}
```

Expected:

```text
Conversation not found
```

### Wrong client API key

Use an invalid `X-API-Key`.

Expected:

```text
403 Forbidden
```

### Duplicate client key

Send `POST /api/v1/clients` with a `clientKey` that already exists:

```json
{
  "clientKey": "demo-client",
  "name": "Duplicate Demo Client",
  "apiKey": "another-api-key"
}
```

Expected: HTTP `409 Conflict`.

Example response:

```json
{
  "timestamp": "2026-10-01T12:00:00Z",
  "status": 409,
  "error": "Conflict",
  "code": "RESOURCE_CONFLICT",
  "message": "Client key 'demo-client' already exists. Use a unique clientKey.",
  "path": "/api/v1/clients"
}
```

### Database schema mismatch

If the application reaches the chat controller but the response fails with an
Oracle error mentioning `SUPPORT_MESSAGES.ROLE` or `SUPPORT_MESSAGES.SENDER_TYPE`,
compare the existing table with the canonical definition in
`src/main/resources/sql/schema.sql`. Older schemas may contain both columns.
Follow the migration instructions in `ORACLE_SQL_SETUP.md` and restart the
application before retrying.

## Recommended execution order

```text
1. Health check
2. Agent login
3. Optional client creation
4. Knowledge ingestion
5. Start customer chat
6. Continue customer chat
7. List conversations as agent
8. List clients as agent
9. Train a project as an agent (list/ingest/file)
10. Dashboard UI walkthrough
11. Error tests: 400 / 403 / 404 / 409 / 429
```

## Endpoint summary

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/actuator/health` | Check application health |
| `GET` | `/actuator/info` | Read application information |
| `GET` | `/` | Agent dashboard (SPA) |
| `POST` | `/api/v1/agent/auth/login` | Authenticate an agent and receive a JWT |
| `GET` | `/api/v1/clients` | List clients |
| `POST` | `/api/v1/clients` | Create a client |
| `POST` | `/api/v1/knowledge` | Ingest client knowledge and embeddings |
| `POST` | `/api/v1/knowledge/file` | Ingest a PDF/Markdown document |
| `GET` | `/api/v1/clients/{id}/knowledge` | List a project's documents (agent) |
| `POST` | `/api/v1/clients/{id}/knowledge` | Ingest text knowledge for a project (agent) |
| `POST` | `/api/v1/clients/{id}/knowledge/file` | Upload PDF/Markdown for a project (agent) |
| `POST` | `/api/v1/support/chat` | Start or continue customer chat |
| `GET` | `/api/v1/agent/conversations` | List conversations for a client |
