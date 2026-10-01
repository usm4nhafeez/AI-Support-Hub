# AI Support Hub - Postman Testing Flow

## Base URL

```text
http://localhost:8000
```

Create a Postman environment with these variables:

```text
baseUrl = http://localhost:8000
clientKey = bookvault
apiKey = change-me
agentUsername = admin
agentPassword = <value configured in IntelliJ>
agentToken =
conversationId =
```

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

## 2. Login as an agent

The demo agent is created automatically when the application starts.

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

This endpoint requires an agent JWT. This step is optional because the application automatically creates the default `bookvault` client during startup.

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
    -> optionally call BookVault tools
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

This endpoint requires an agent JWT and `X-Client-Key`.

### Request

```http
GET {{baseUrl}}/api/v1/agent/conversations
Authorization: Bearer {{agentToken}}
X-Client-Key: {{clientKey}}
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

## Authentication matrix

| Endpoint | Authentication |
|---|---|
| `GET /actuator/health` | Public |
| `GET /actuator/info` | Public |
| `POST /api/v1/agent/auth/login` | Public |
| `POST /api/v1/support/chat` | `X-Client-Key` + `X-API-Key` |
| `POST /api/v1/knowledge` | `X-Client-Key` + `X-API-Key` |
| `GET /api/v1/agent/conversations` | `Authorization: Bearer ...` + `X-Client-Key` |
| `POST /api/v1/clients` | `Authorization: Bearer ...` |

## Important Postman details

Customer requests require these exact headers:

```http
X-Client-Key: bookvault
X-API-Key: change-me
Content-Type: application/json
```

Do not use only a Bearer token for customer chat. Customer endpoints authenticate through the API-key filter.

Agent requests require:

```http
Authorization: Bearer {{agentToken}}
```

The agent conversation endpoint also requires:

```http
X-Client-Key: {{clientKey}}
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
  "clientKey": "bookvault",
  "name": "BookVault Duplicate",
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
  "message": "Client key 'bookvault' already exists. Use a unique clientKey.",
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
```

## Endpoint summary

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/actuator/health` | Check application health |
| `GET` | `/actuator/info` | Read application information |
| `POST` | `/api/v1/agent/auth/login` | Authenticate an agent and receive a JWT |
| `POST` | `/api/v1/clients` | Create a client |
| `POST` | `/api/v1/knowledge` | Ingest client knowledge and embeddings |
| `POST` | `/api/v1/support/chat` | Start or continue customer chat |
| `GET` | `/api/v1/agent/conversations` | List conversations for a client |
