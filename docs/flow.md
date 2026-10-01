# AI Support Hub flow

## Application layers

- **Controller layer** receives HTTP requests and maps them to use cases.
- **Security filters** set the authenticated identity from either client API keys or agent JWTs.
- **Service layer** contains business logic and coordinates repository, AI, vector search, and tool calls.
- **Repository layer** persists and reads data from the database.
- **AI/tool layer** handles Gemini, vector search, and external tool calls.

## Request flow by route

### `POST /api/v1/clients`

1. `ClientController.create(...)` accepts a validated `CreateClientRequest`.
2. `ClientService.create(...)` creates a `Client`.
3. The API key is hashed before storage.
4. `ClientRepository.save(...)` persists the client.
5. The created `Client` is returned.

### `POST /api/v1/agent/auth/login`

1. `AgentAuthController.login(...)` accepts a validated `AgentLoginRequest`.
2. `AgentService.authenticate(...)` loads the agent by username.
3. The password is verified against the stored hash.
4. `JwtService.generateToken(...)` creates a JWT with agent identity and role.
5. `AgentLoginResponse` returns the token.

### `GET /api/v1/agent/conversations`

1. `ApiKeyFilter` is not used here; `JwtAuthenticationFilter` authenticates the request if a Bearer token is present.
2. `AgentController.conversations(...)` reads `X-Client-Key`.
3. `ClientService.getByKey(...)` resolves the client.
4. `ConversationService.list(...)` loads conversations for that client.
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
   - `BookVaultTools`
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
7. The saved knowledge document is returned.

## Supporting flows

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
4. It exposes `BookVaultTools` to the model.
5. It calls the chat model.
6. It audits the AI response.

### Tool execution

- `BookVaultTools.getBookDetails(...)` calls the BookVault API.
- If the tool fails, it returns a safe fallback message instead of inventing data.

### Audit flow

- `AuditService.log(...)` stores audit events with client, conversation, actor type, event type, and metadata.

## Error handling

- Validation failures return `400` with `{"error":"Invalid request"}`.
- Missing entities such as client or conversation return `404`.
- AI/tool failures return `502`.

## Route-to-layer summary

| Route | Main layers involved |
| --- | --- |
| `POST /api/v1/clients` | Controller -> Service -> Repository |
| `POST /api/v1/agent/auth/login` | Controller -> Service -> Security/JWT |
| `GET /api/v1/agent/conversations` | Security/JWT -> Controller -> Service -> Repository |
| `POST /api/v1/support/chat` | Security/API key -> Controller -> Service -> AI/Tools -> Repository |
| `POST /api/v1/knowledge` | Security/API key -> Controller -> Service -> Vector Store -> Repository |

