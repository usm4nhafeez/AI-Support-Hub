# API Integration Guide — Embed the Support Bot in Your Project

This guide explains how any application (web app, mobile app, backend service, website widget)
calls the AI Support Hub chatbot using its own **client key** and **API key**.

Base URL (local development):

```text
http://localhost:8000
```

---

## 1. How authentication works

Every project that uses the bot is registered as a **client** (tenant) with two credentials:

| Credential | Header | Notes |
|---|---|---|
| Client key | `X-Client-Key` | Public identifier of your project, e.g. `my-app-a1b2c3` |
| API key | `X-API-Key` | Secret. Shown **once** at creation, stored hashed. Never commit it. |

- **Customer-facing endpoints** (`/api/v1/support/chat`, `/api/v1/knowledge`) authenticate with
  `X-Client-Key` + `X-API-Key` → role `ROLE_CLIENT`.
- **Agent/admin endpoints** (`/api/v1/clients`, `/api/v1/agent/**`, `/api/v1/clients/{id}/knowledge`)
  authenticate with `Authorization: Bearer <JWT>` → role `ROLE_AGENT`.
- **Never send both** an `Authorization` header and the client headers on the same request —
  each filter skips the request when the other credential type is present, so mixing them fails.
- Knowledge and conversations are strictly isolated per client: a project can only see and train
  its own data.

### Get your keys

1. Log in to the dashboard as an agent and open **Clients**.
2. Enter a project name — the hub generates a unique client key and a strong API key.
3. Copy both from the credential ticket into your application's environment, e.g.
   `SUPPORT_CLIENT_KEY` and `SUPPORT_API_KEY`.

Lost the API key? It cannot be recovered (only the hash is stored). Rotate it instead —
the project keeps its client key, tenant id and all trained knowledge, and only the
API secret changes:

```bash
curl -X POST http://localhost:8000/api/v1/clients/123/rotate-api-key \
  -H "Authorization: Bearer $AGENT_JWT"
# → {"id":123,"clientKey":"my-app","name":"My App","apiKey":"ash_…","rotatedAt":"…"}
```

The new plaintext `apiKey` is returned **once**, in this response only; the previous
key stops working immediately. In the dashboard: **Clients → Rotate key** on the row,
which shows the new pair on a credential ticket.

Programmatic alternative to register a brand-new project (requires an agent JWT):

```bash
curl -X POST http://localhost:8000/api/v1/clients \
  -H "Authorization: Bearer $AGENT_JWT" \
  -H "Content-Type: application/json" \
  -d '{"clientKey":"my-app","name":"My App","apiKey":"a-long-random-secret"}'
```

`clientKey` is unique in the database, but a duplicate never fails the request:
the hub auto-issues a suffixed variant (e.g. `my-app-1a2b3c`) and the **response's
`clientKey` is the authoritative value** — store that one. A `409 Conflict` is only
returned if a unique key could not be allocated after several attempts (rare).

---

## 2. Chat with the bot

```text
POST /api/v1/support/chat
X-Client-Key: <your-client-key>
X-API-Key: <your-api-key>
Content-Type: application/json
```

Request body:

| Field | Required | Description |
|---|---|---|
| `customerId` | yes | Your user's id. A conversation is created on the first message. |
| `conversationId` | no | Returned `conversationId` from a previous reply — send it to continue the same conversation with full context. |
| `message` | yes | The customer's question. |

Example:

```bash
curl -X POST http://localhost:8000/api/v1/support/chat \
  -H "Content-Type: application/json" \
  -H "X-Client-Key: $SUPPORT_CLIENT_KEY" \
  -H "X-API-Key: $SUPPORT_API_KEY" \
  -d '{"customerId":"user-123","message":"What is the return policy?"}'
```

Response:

```json
{
  "conversationId": "8f2c…-uuid",
  "status": "AI_HANDLING",
  "response": "Customers may return eligible items within 30 days…"
}
```

Continue the same conversation by adding `conversationId` to the next request:

```json
{ "customerId": "user-123", "conversationId": "8f2c…-uuid", "message": "Can I speak to a human?" }
```

The `conversationId` must belong to your client; other tenants' ids return **"Conversation not found"**.

Conversation `status` values: `OPEN`, `AI_HANDLING`, `WAITING_FOR_CUSTOMER`, `WAITING_FOR_AGENT`,
`ASSIGNED`, `RESOLVED`.

> **Security rule:** call this endpoint from **your backend** and proxy the reply to your
> frontend. Sending `X-API-Key` from browser code exposes your project's secret.

---

## 3. Train your project's knowledge

The bot answers from your project's documents via RAG (vector search scoped to your client key).
Ingest knowledge with the same client credentials.

### Text

```text
POST /api/v1/knowledge
```

```bash
curl -X POST http://localhost:8000/api/v1/knowledge \
  -H "Content-Type: application/json" \
  -H "X-Client-Key: $SUPPORT_CLIENT_KEY" \
  -H "X-API-Key: $SUPPORT_API_KEY" \
  -d '{
    "name": "Return Policy",
    "category": "policies",
    "version": "1.0",
    "content": "Customers may return eligible items within 30 days of delivery…"
  }'
```

`name` and `content` are required; `category` and `version` are optional. The content is
chunked, embedded, and stored in the Oracle vector store scoped to your client.

### Files (PDF / Markdown)

```text
POST /api/v1/knowledge/file
Content-Type: multipart/form-data
```

```bash
curl -X POST http://localhost:8000/api/v1/knowledge/file \
  -H "X-Client-Key: $SUPPORT_CLIENT_KEY" \
  -H "X-API-Key: $SUPPORT_API_KEY" \
  -F "file=@handbook.pdf" \
  -F "name=Handbook" \
  -F "category=manuals"
```

Accepted extensions: `.pdf`, `.md`, `.markdown`. Any other file returns `400`.
Maximum upload size is 25 MB per file (larger uploads return `413`).

### As an agent (no client API key needed)

Agents can train any project with a JWT instead of the project's API key:

```text
GET  /api/v1/clients                       # list projects
GET  /api/v1/clients/{clientId}/knowledge  # list a project's documents
POST /api/v1/clients/{clientId}/knowledge  # ingest text (same JSON body)
POST /api/v1/clients/{clientId}/knowledge/file  # upload PDF/Markdown
Authorization: Bearer <agent JWT>
```

This is what the dashboard's **Knowledge** screen uses.

---

## 4. Agent endpoints (dashboard / ops integrations)

| Method | Endpoint | Auth | Purpose |
|---|---|---|---|
| `POST` | `/api/v1/agent/auth/login` | public | `{username, password}` → `{username, token}` JWT |
| `GET` | `/api/v1/clients` | JWT | List clients |
| `POST` | `/api/v1/clients` | JWT | Register a client |
| `POST` | `/api/v1/clients/{id}/rotate-api-key` | JWT | Issue a fresh API secret for an existing project (keeps id, client key and knowledge; plaintext key returned once) |
| `GET` | `/api/v1/clients/{id}/agents` | JWT | List a project's support agents (`id, username, role, active, createdAt` — no secrets) |
| `POST` | `/api/v1/clients/{id}/agents` | JWT | Add an agent login (`{username, password}`, min 3/8 chars; stored as BCrypt hash). Usernames are globally unique — a taken name is issued as `name-xxxx`, and the response username is the exact one to sign in with |
| `DELETE` | `/api/v1/clients/{id}/agents/{agentId}` | JWT | Remove an agent login → `{id, username, ticketsReleased}`. Only the login goes: tickets still assigned to that agent return to the queue (`ASSIGNED` → `OPEN`), the username becomes reusable, and the project, its keys and its trained knowledge are untouched. Refused with `409 RESOURCE_CONFLICT` for your own account and for a project's last active admin; another project's agent id answers `404` |
| `GET` | `/api/v1/agent/conversations` | JWT | List your client's conversations (summary, status, assigned agent) |
| `GET` | `/api/v1/agent/team` | JWT | List your own teammates — feeds the assign dropdown |
| `POST` | `/api/v1/agent/conversations/{id}/assign` | JWT | Assign a conversation to a teammate (`{"username":"…"}`; body omitted or blank → assign to yourself; target must be an active agent of your tenant). Status → `ASSIGNED` |
| `DELETE` | `/api/v1/agent/conversations/{id}/assign` | JWT | Release an assigned conversation back to the queue |
| `GET/POST` | `/api/v1/clients/{id}/knowledge[/file]` | JWT | Read/train project knowledge |
| `GET` | `/actuator/health` | public | Liveness check |

---

## 5. Errors

Every error returns the same JSON shape:

```json
{
  "timestamp": "2026-10-12T09:30:00Z",
  "status": 429,
  "error": "Too Many Requests",
  "code": "RATE_LIMITED",
  "message": "Too many requests. Please retry in 24 seconds.",
  "path": "/api/v1/support/chat"
}
```

| Status | Code (typical) | Meaning | What to do |
|---|---|---|---|
| `400` | `VALIDATION_ERROR`, `MALFORMED_REQUEST` | Missing/blank `customerId` or `message`, unsupported file type, unreadable JSON | Fix the request body |
| `401` | `INVALID_CREDENTIALS` | Bad agent username/password or invalid/expired JWT on login | Re-login |
| `403` | (no JSON body — Spring Security) | Missing/wrong `X-Client-Key`/`X-API-Key`, or `Authorization` header mixed with client headers, or client-role call to an agent route | Send both client headers, drop the Bearer token |
| `404` | `NOT_FOUND`, `RESOURCE_NOT_FOUND` | Unknown route, missing conversation/client | Check path and ids |
| `409` | `RESOURCE_CONFLICT`, `DATA_CONFLICT` | `clientKey` collision could not be auto-resolved (rare — duplicates are normally issued a unique suffix) | Retry onboarding |
| `413` | `UPLOAD_TOO_LARGE` | Knowledge file upload exceeds 25 MB | Split or shrink the document |
| `429` | `RATE_LIMITED` | Rate limit hit (see below) | Retry after the `Retry-After` header seconds |
| `500` | `INTERNAL_ERROR` | Unexpected server failure (stack trace logged server-side only) | Retry; report with the timestamp |
| `502` | `UPSTREAM_ERROR` | Gemini unavailable or bad `GEMINI_API_KEY` | Retry; check provider config |

Except for `403`, every error above is a JSON `ApiError` object as shown in the example.

---

## 6. Rate limits

| Endpoint | Limit | Keyed by |
|---|---|---|
| `POST /api/v1/agent/auth/login` | 10 requests / minute | client IP |
| `POST /api/v1/support/chat` | 30 requests / minute | `X-Client-Key`, falling back to IP |

Exceeding a limit returns `429` with a `Retry-After` header in seconds.

---

## 7. Minimal end-to-end integration

```bash
# 0. (once, per environment) train knowledge
curl -X POST http://localhost:8000/api/v1/knowledge \
  -H "Content-Type: application/json" \
  -H "X-Client-Key: $SUPPORT_CLIENT_KEY" -H "X-API-Key: $SUPPORT_API_KEY" \
  -d '{"name":"FAQ","content":"Shipping takes 2-4 business days."}'

# 1. first question — conversation created implicitly
curl -X POST http://localhost:8000/api/v1/support/chat \
  -H "Content-Type: application/json" \
  -H "X-Client-Key: $SUPPORT_CLIENT_KEY" -H "X-API-Key: $SUPPORT_API_KEY" \
  -d '{"customerId":"user-42","message":"How long does shipping take?"}'
# → {"conversationId":"abc…","status":"AI_HANDLING","response":"Shipping takes 2-4 business days."}

# 2. follow-up in the same conversation
curl -X POST http://localhost:8000/api/v1/support/chat \
  -H "Content-Type: application/json" \
  -H "X-Client-Key: $SUPPORT_CLIENT_KEY" -H "X-API-Key: $SUPPORT_API_KEY" \
  -d '{"customerId":"user-42","conversationId":"abc…","message":"And to Europe?"}'
```

Store `conversationId` alongside `customerId` in your app so each user keeps one continuous thread.

---

## 8. The dashboard also documents this

The in-app **API Guide** screen (left sidebar) mirrors sections 2–6 above and can copy the
curl example with your currently loaded credentials. Use the UI guide for quick copy-paste;
use this file as the canonical reference for code review and onboarding docs.
