# AI Support Hub flow

## Application layers

- **Web UI layer** (`src/main/resources/static`) serves the agent dashboard SPA: login, conversations, chat playground, knowledge training, client onboarding, the in-app API Guide, and an About screen with the developer's GitHub and contact links. It ships three themes and a vendored Three.js routing-core visual, and every screen is deep-linked by hash (`/#clients`).
- **Controller layer** receives HTTP requests and maps them to use cases.
- **Security filters** rate-limit abuse paths, then set the authenticated identity from either client API keys or agent JWTs.
- **Service layer** contains business logic and coordinates repository, AI, vector search, and tool calls.
- **Repository layer** persists and reads data from the database.
- **AI/tool layer** handles Gemini, vector search, and external tool calls.

## Request flow by route

### `POST /api/v1/clients`

1. `ClientController.create(...)` accepts a validated `CreateClientRequest`.
2. `ClientService.create(...)` creates a `Client`.
3. The API key is hashed before storage.
4. `ClientRepository.save(...)` persists the client.
5. The created client is mapped to a `ClientResponse`; the API-key hash is never returned.

### `GET /api/v1/clients`

1. `JwtAuthenticationFilter` authenticates the agent (`ROLE_AGENT` required).
2. `ClientController.list(...)` calls `ClientService.listAll()` (sorted by name).
3. Clients are mapped to `ClientResponse` objects; no credential material is serialized.

### `GET|POST /api/v1/clients/{clientId}/knowledge[/file]` (`AgentKnowledgeController`)

1. Authenticated with an agent JWT — the project's API key is never needed.
2. `clientService.getById(clientId)` validates the tenant (`404` if unknown).
3. `GET` lists documents via `KnowledgeDocumentRepository.findByClientIdOrderByCreatedAtDesc(...)`.
4. `POST` (JSON) reuses `KnowledgeService.ingest(...)`; `POST /file` reuses `KnowledgeService.ingestFile(...)` (PDF/Markdown only).
5. The dashboard's Knowledge screen uses these routes to train any project.

### `POST /api/v1/agent/auth/login`

1. `AgentAuthController.login(...)` accepts a validated `AgentLoginRequest`.
2. `AgentService.authenticate(...)` loads the agent by username.
3. The password is verified against the stored hash.
4. `JwtService.generateToken(...)` creates a JWT with agent identity and role.
5. `AgentLoginResponse` returns the token.

### `GET /api/v1/agent/conversations`

1. `ApiKeyFilter` is not used here; `JwtAuthenticationFilter` authenticates the request if a Bearer token is present.
2. `JwtAuthenticationFilter` places the signed JWT subject and client ID in `AgentPrincipal`.
3. `AgentController.conversations(...)` derives the tenant from `AgentPrincipal`.
4. `ConversationService.listByClientId(...)` loads conversations for that tenant.
5. The conversation list is returned.

### `POST /api/v1/support/chat`

1. `ApiKeyFilter` authenticates the client from `X-Client-Key` + `X-API-Key`.
2. `SupportController.chat(...)` resolves the client with `ClientService.getByKey(...)`.
3. `ConversationService.getOrCreate(...)` loads an existing conversation or creates a new one.
4. The customer message is saved with `ConversationService.addMessage(...)`.
5. `SupportAiService.answer(...)` builds the prompt from:
   - current client
   - recent conversation history
   - user question
   - vector search advisor
   - configured tenant knowledge
6. The AI response is saved as a second message.
7. The conversation status is updated.
8. `ChatResponse` returns the conversation key, status, and answer.

### `POST /api/v1/knowledge`

1. `ApiKeyFilter` authenticates the client.
2. `KnowledgeController.ingest(...)` resolves the client.
3. `KnowledgeService.ingest(...)` saves a `KnowledgeDocument`.
4. The content is wrapped in a Spring AI `Document` with client metadata.
5. `TokenTextSplitter` chunks the content.
6. `VectorStore.add(...)` stores the chunks for retrieval.
7. The saved document is mapped to a `KnowledgeResponse`; client credentials are never serialized.

## Supporting flows

### `RateLimitFilter`

- Runs before authentication filters (instantiated in `SecurityConfig`, not a `@Component`, to avoid double registration).
- Fixed one-minute windows keyed per path: `POST /api/v1/agent/auth/login` allows 10/min per IP; `POST /api/v1/support/chat` allows 30/min per `X-Client-Key` (falling back to IP).
- Over the limit: returns `429` with a `Retry-After` header and an `ApiError` JSON body (`code: RATE_LIMITED`).

### `ApiKeyFilter`

- Reads `X-Client-Key` and `X-API-Key`.
- Resolves the client and checks the hashed API key.
- Sets `ROLE_CLIENT` in the security context on success.

### `JwtAuthenticationFilter`

- Reads the `Authorization: Bearer ...` header.
- Validates the JWT.
- Sets `ROLE_AGENT` in the security context on success.

### AI answer generation

1. `SupportAiService.answer(...)` loads recent messages.
2. It creates a text history block for the prompt.
3. It applies a client-scoped vector search advisor.
4. It calls the chat model.
5. It audits the AI response.

### Audit flow

- `AuditService.log(...)` stores audit events with client, conversation, actor type, event type, and metadata.

## Error handling

All `GlobalExceptionHandler` responses share the `ApiError` shape:
`{timestamp, status, error, code, message, path}`. Stack traces are logged
server-side only and never returned to clients.

| Situation | Status | Code |
| --- | --- | --- |
| Bean-validation failure on a request body | `400` | `VALIDATION_ERROR` |
| Unreadable JSON / unsupported file extension | `400` | `MALFORMED_REQUEST` |
| Wrong agent login credentials | `401` | `INVALID_CREDENTIALS` |
| Missing/wrong client headers or mixed auth styles (Spring Security, no JSON body) | `403` | — |
| Unknown route/resource, missing conversation or client | `404` | `NOT_FOUND` / `RESOURCE_NOT_FOUND` |
| Duplicate client key / integrity clash | `409` | `RESOURCE_CONFLICT` / `DATA_CONFLICT` |
| Rate limit exceeded | `429` | `RATE_LIMITED` (with `Retry-After` header) |
| Upstream AI/tool failure | `502` | `UPSTREAM_ERROR` |
| Any other exception | `500` | `INTERNAL_ERROR` |

## Route-to-layer summary

| Route | Main layers involved |
| --- | --- |
| `POST /api/v1/clients` | Security/JWT -> Controller -> Service -> Repository |
| `GET /api/v1/clients` | Security/JWT -> Controller -> Service -> Repository |
| `POST /api/v1/agent/auth/login` | Rate limit -> Controller -> Service -> Security/JWT |
| `GET /api/v1/agent/conversations` | Security/JWT -> Controller -> Service -> Repository |
| `POST /api/v1/support/chat` | Rate limit -> Security/API key -> Controller -> Service -> AI/Tools -> Repository |
| `POST /api/v1/knowledge` | Security/API key -> Controller -> Service -> Vector Store -> Repository |
| `POST /api/v1/knowledge/file` | Security/API key -> Controller -> Service (PDF/MD reader) -> Vector Store -> Repository |
| `GET/POST /api/v1/clients/{id}/knowledge[/file]` | Security/JWT -> Controller -> Service -> Vector Store/Repository |
| `GET /`, static assets | Web UI layer (public, no auth) |

