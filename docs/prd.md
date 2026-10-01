# AI Support Hub

## Product Requirements Document

**Version:** 1.1
**Status:** Proposed
**Product Type:** Reusable AI Customer Support Platform
**Architecture:** Service-Tier / Layered Architecture
**Backend:** Java + Spring Boot
**AI:** Spring AI
**Database:** Oracle Database + Oracle Vector Search
**Deployment:** Docker
**Primary Integration:** REST API
**Future Integration:** Spring Boot Starter

---

# 1. Product Overview

## 1.1 Product Name

**AI Support Hub**

## 1.2 Product Summary

AI Support Hub is a reusable AI-powered customer-support service that can be integrated into independent applications through REST APIs.

The platform provides:

* AI-powered customer conversations.
* Client-specific knowledge bases.
* RAG-based knowledge retrieval.
* Conversation memory.
* Application-data access through tools.
* Controlled business actions.
* Human-agent escalation.
* Support ticket management.
* Agent workspace.
* Analytics.
* Audit logging.

The platform itself is **application-independent**.

A client application does not need to implement Spring AI. It only needs to communicate with AI Support Hub through its API.

---

# 2. Problem Statement

Applications commonly need customer-support functionality, but implementing an intelligent support system requires multiple components:

* AI integration.
* Knowledge retrieval.
* Conversation management.
* Customer context.
* Business-data access.
* Support tickets.
* Human escalation.
* Agent management.
* Analytics.

A generic chatbot cannot reliably answer application-specific questions because it does not have access to real-time application data.

AI Support Hub provides a centralized support service that combines:

```text
AI
+
Knowledge
+
Application Data
+
Business Tools
+
Human Support
```

---

# 3. Product Vision

Build a reusable AI customer-support service that can be connected to different applications without coupling the applications to the AI implementation.

The platform should allow an application to add AI support simply by integrating with the AI Support Hub REST API.

---

# 4. Target Users

## 4.1 End Customer

The customer using a client application.

Examples:

```text
"Where is my order?"

"Can I return this product?"

"Why was I charged twice?"

"How do I reset my password?"
```

---

## 4.2 Support Agent

A human representative who handles conversations that AI cannot safely or effectively resolve.

---

## 4.3 Client Application Developer

A developer integrating AI Support Hub into an existing application.

The developer should be able to:

* Register an application.
* Obtain API credentials.
* Configure the application's AI behavior.
* Upload knowledge.
* Register business tools.
* Connect the application's support UI to the API.

---

## 4.4 Platform Administrator

The administrator responsible for managing:

* Clients.
* Agents.
* Teams.
* Knowledge.
* Tools.
* Platform configuration.
* Analytics.

---

# 5. Core Product Principle

AI Support Hub is **not a chatbot application tied to one business**.

It is a reusable service:

```text
┌──────────────────┐
│ Client App A     │
│ E-Commerce       │
└────────┬─────────┘
         │
         │ REST
         ▼
┌─────────────────────────┐
│                         │
│     AI Support Hub      │
│                         │
└─────────────────────────┘
         ▲
         │ REST
┌────────┴─────────┐
│ Client App B     │
│ SaaS Application │
└──────────────────┘
```

---

# 6. Goals

## Primary Goals

1. Build a reusable Spring Boot support service.
2. Use service-tier architecture.
3. Expose a REST API for external applications.
4. Support multiple client applications.
5. Isolate each client's data and configuration.
6. Implement RAG using Oracle Vector Search.
7. Implement AI tool calling.
8. Support real-time client application data.
9. Support human-agent escalation.
10. Provide controlled business actions.
11. Maintain conversation history.
12. Maintain audit logs.
13. Provide support analytics.
14. Containerize the application with Docker.
15. Deploy the platform to a cloud environment.

---

# 7. Non-Goals

The MVP will not:

* Replace human support teams completely.
* Automatically perform sensitive financial operations without authorization.
* Become a general-purpose AI assistant.
* Build a complete CRM.
* Require every client application to use Spring AI.
* Require every client application to use Java.

The REST API should remain technology-independent.

---

# 8. Architecture

## 8.1 Service-Tier Architecture

The platform will follow a layered/service-tier architecture.

```text
Client
  ↓
Controller
  ↓
Service
  ↓
Repository
  ↓
Database
```

AI-specific functionality will also be encapsulated behind services.

```text
Controller
    ↓
SupportService
    ├── ConversationService
    ├── RagService
    ├── ToolService
    ├── EscalationService
    └── KnowledgeService
           ↓
      Repository Layer
           ↓
       Oracle DB
```

---

# 9. Backend Package Structure

```text
com.aisupporthub
│
├── controller
│   ├── SupportController
│   ├── ConversationController
│   ├── AgentController
│   ├── ClientController
│   ├── KnowledgeController
│   └── ToolController
│
├── service
│   ├── SupportService
│   ├── ConversationService
│   ├── MessageService
│   ├── AgentService
│   ├── ClientService
│   ├── KnowledgeService
│   ├── RagService
│   ├── ToolService
│   ├── EscalationService
│   ├── TicketService
│   └── AnalyticsService
│
├── repository
│   ├── ClientRepository
│   ├── CustomerRepository
│   ├── ConversationRepository
│   ├── MessageRepository
│   ├── AgentRepository
│   ├── TicketRepository
│   ├── KnowledgeRepository
│   └── ToolRepository
│
├── ai
│   ├── SupportAgent
│   ├── PromptService
│   ├── MemoryService
│   └── ToolExecutionService
│
├── integration
│   ├── ClientApiService
│   └── WebhookService
│
├── model
│   ├── entity
│   ├── dto
│   └── enums
│
├── security
│   ├── JwtService
│   ├── ApiKeyService
│   └── SecurityConfig
│
└── config
```

Controllers must remain thin.

Business logic belongs in services.

Database operations belong in repositories.

AI operations belong in dedicated AI/service components.

---

# 10. Integration Architecture

## 10.1 Primary Integration — REST API

Any application capable of making HTTP requests can integrate with AI Support Hub.

```text
┌──────────────────────┐
│ Spring Boot App      │
│                      │
│ BookVault            │
└──────────┬───────────┘
           │
           │ HTTPS
           ▼
┌──────────────────────┐
│ AI Support Hub       │
│ Spring Boot          │
└──────────────────────┘
```

The client application does not need:

* Spring AI.
* Vector database.
* LLM SDK.
* RAG implementation.

It only needs the AI Support Hub API.

---

# 11. Spring Boot Integration

A Spring Boot application must be able to integrate using standard HTTP communication.

Example:

```http
POST /api/v1/support/chat
```

Request:

```json
{
  "clientId": "bookvault",
  "customerId": "user-123",
  "conversationId": "conv-456",
  "message": "Is Clean Code available?"
}
```

AI Support Hub processes the request.

If required, it can call a BookVault API:

```text
AI Support Hub
      ↓
getBookDetails()
      ↓
BookVault API
      ↓
Book information
      ↓
AI response
```

---

# 12. Future Spring Boot Starter

A future version may provide:

```text
ai-support-hub-spring-boot-starter
```

Example:

```xml
<dependency>
    <groupId>com.aisupporthub</groupId>
    <artifactId>ai-support-hub-spring-boot-starter</artifactId>
</dependency>
```

Configuration:

```properties
ai.support.hub.url=https://support.example.com
ai.support.hub.client-id=bookvault
ai.support.hub.api-key=${AI_SUPPORT_API_KEY}
```

The starter is **not part of MVP**.

REST integration is the primary integration mechanism.

---

# 13. Multi-Tenancy

AI Support Hub must support multiple client applications.

Each client receives:

```text
clientId
API credentials
AI configuration
Knowledge base
Tools
Agents
Conversations
Customers
```

Example:

```text
Client A
├── Knowledge
├── Tools
├── Customers
└── Conversations

Client B
├── Knowledge
├── Tools
├── Customers
└── Conversations
```

Client A must never access Client B's data.

---

# 14. Customer Support Flow

```text
Customer
   ↓
Client Application
   ↓
AI Support Hub API
   ↓
SupportService
   ↓
AI Orchestration
   ├── Conversation Memory
   ├── RAG
   └── Tool Calling
   ↓
Response
   ↓
Client Application
   ↓
Customer
```

---

# 15. AI Decision Flow

For every customer message:

```text
Customer Message
       ↓
Understand Intent
       ↓
Check Conversation Context
       ↓
Determine Required Information
       ↓
Knowledge Retrieval / Tool Call
       ↓
Generate Response
       ↓
Policy / Safety Check
       ↓
Response or Escalation
```

The AI should not automatically use tools for every request.

Example:

```text
"What is your return policy?"
        ↓
RAG

"Where is my order?"
        ↓
Order API tool

"Refund my payment."
        ↓
Payment lookup
        ↓
Authorization
        ↓
Human approval if required
```

---

# 16. Knowledge Management

Each client has its own knowledge base.

Supported sources:

* PDF.
* DOCX.
* TXT.
* Markdown.
* FAQ.
* Manual articles.
* API-provided knowledge.

Pipeline:

```text
Document
   ↓
Text Extraction
   ↓
Chunking
   ↓
Embedding
   ↓
Oracle Vector Search
```

Each vector must contain metadata such as:

```text
clientId
documentId
documentName
category
version
createdAt
```

Retrieval must filter by `clientId`.

---

# 17. Tool Integration

Client applications can expose business operations.

Example:

```text
getOrderStatus()
getCustomerOrders()
getProductDetails()
getSubscription()
getInvoice()
createSupportTicket()
```

AI Support Hub executes the tools through controlled backend services.

The LLM must not receive unrestricted database access.

---

# 18. Tool Risk Levels

Every tool must have a risk classification.

```text
READ_ONLY
LOW_RISK_ACTION
HIGH_RISK_ACTION
```

Examples:

```text
getOrderStatus()       → READ_ONLY
createTicket()         → LOW_RISK_ACTION
cancelOrder()          → HIGH_RISK_ACTION
requestRefund()        → HIGH_RISK_ACTION
```

High-risk operations require explicit authorization.

```text
AI Recommendation
       ↓
Customer / Agent Approval
       ↓
Backend Authorization
       ↓
Tool Execution
       ↓
Audit Log
```

---

# 19. Conversation Management

Each conversation must contain:

```text
conversationId
clientId
customerId
status
priority
assignedAgent
createdAt
updatedAt
summary
```

Conversation statuses:

```text
OPEN
AI_HANDLING
WAITING_FOR_CUSTOMER
WAITING_FOR_AGENT
ASSIGNED
RESOLVED
CLOSED
```

---

# 20. Support Agent Requirements

Support agents must be able to:

* View support queues.
* View conversations.
* Claim conversations.
* Assign conversations.
* Reassign conversations.
* View customer context.
* View AI summaries.
* View retrieved knowledge.
* View tool execution history.
* Add internal notes.
* Send customer messages.
* Use AI-generated response suggestions.
* Edit AI suggestions.
* Approve actions.
* Reject actions.
* Resolve conversations.
* Reopen conversations.
* Change priority.

---

# 21. Support Queue

The support queue should display:

```text
Conversation ID
Customer
Priority
Status
Category
Assigned Agent
Created Time
Escalation Reason
```

Agents should be able to filter by:

```text
Status
Priority
Agent
Team
Category
Client
Date
```

---

# 22. AI Escalation

AI must escalate when:

* Customer requests a human.
* AI cannot confidently resolve the issue.
* Required information is unavailable.
* Sensitive operations are requested.
* Multiple failed attempts occur.
* Business policy requires human approval.
* Client configuration requires escalation.

Escalation record:

```text
conversationId
reason
priority
summary
recommendedAction
createdAt
```

---

# 23. Agent Workspace

The workspace should display:

```text
┌─────────────────────────────────────────┐
│ Customer Conversation                   │
├─────────────────────────────────────────┤
│ Customer messages                       │
│ AI responses                            │
│ Agent responses                         │
├─────────────────────────────────────────┤
│ Customer Context                        │
│ Order / Account / Subscription          │
├─────────────────────────────────────────┤
│ AI Summary                              │
├─────────────────────────────────────────┤
│ Retrieved Knowledge                     │
├─────────────────────────────────────────┤
│ Tool Execution                          │
├─────────────────────────────────────────┤
│ AI Suggested Response                   │
│ [Edit] [Send]                           │
└─────────────────────────────────────────┘
```

---

# 24. Internal Notes

Agents can add private notes.

Example:

```text
Customer has contacted support multiple times.
Escalate to billing.
```

Internal notes must never be returned through customer-facing APIs.

---

# 25. Ticket Management

The system should support support tickets.

Ticket fields:

```text
ticketId
conversationId
clientId
customerId
category
priority
status
assignedAgent
description
createdAt
updatedAt
```

Ticket statuses:

```text
OPEN
IN_PROGRESS
WAITING
RESOLVED
CLOSED
```

---

# 26. API Requirements

## Customer APIs

```http
POST /api/v1/support/chat
GET /api/v1/support/conversations/{id}
GET /api/v1/support/conversations/{id}/messages
```

## Client APIs

```http
POST /api/v1/clients
GET /api/v1/clients/{id}
PUT /api/v1/clients/{id}
POST /api/v1/clients/{id}/tools
POST /api/v1/clients/{id}/knowledge
```

## Agent APIs

```http
GET /api/v1/agent/conversations
GET /api/v1/agent/conversations/{id}
POST /api/v1/agent/conversations/{id}/assign
POST /api/v1/agent/conversations/{id}/messages
POST /api/v1/agent/conversations/{id}/notes
POST /api/v1/agent/conversations/{id}/resolve
POST /api/v1/agent/conversations/{id}/reopen
```

## Action APIs

```http
POST /api/v1/actions/{id}/approve
POST /api/v1/actions/{id}/reject
```

---

# 27. Authentication

The platform should support:

### Client Authentication

API keys or OAuth-style credentials.

### Agent Authentication

JWT-based authentication.

### Customer Authentication

The client application remains responsible for authenticating its customers.

AI Support Hub receives the authenticated customer's identity/context from the client application.

---

# 28. Authorization

Roles:

```text
ADMIN
AGENT
CLIENT
CUSTOMER
```

Example:

```text
CUSTOMER
→ Own conversations

AGENT
→ Assigned/team conversations

ADMIN
→ Platform management

CLIENT
→ Own client configuration
```

---

# 29. Database

Oracle Database will store:

```text
CLIENT
CUSTOMER
AGENT
TEAM
CONVERSATION
MESSAGE
TICKET
KNOWLEDGE_DOCUMENT
TOOL
TOOL_EXECUTION
ACTION_APPROVAL
AUDIT_EVENT
```

Oracle Vector Search will store embeddings for knowledge retrieval.

---

# 30. Audit Logging

The system must record important events:

```text
Conversation created
Message received
AI response generated
Knowledge retrieved
Tool executed
Action approved
Action rejected
Agent assigned
Conversation escalated
Conversation resolved
```

Audit record:

```text
eventId
clientId
conversationId
actorType
actorId
eventType
timestamp
metadata
```

---

# 31. Analytics

The platform should provide:

```text
Total Conversations
AI Resolution Rate
Human Escalation Rate
Average Response Time
Average Resolution Time
Open Conversations
Resolved Conversations
Tool Success Rate
Top Support Categories
```

Future analytics can include:

```text
Token Usage
AI Cost
Agent Performance
Customer Satisfaction
```

---

# 32. Security Requirements

The platform must:

* Isolate client data.
* Authenticate API requests.
* Authorize operations.
* Protect API keys.
* Never commit secrets to Git.
* Validate client IDs.
* Validate tool permissions.
* Require approval for sensitive actions.
* Audit business actions.
* Protect internal agent notes.
* Use HTTPS in production.

---

# 33. Error Handling

AI Support Hub must gracefully handle:

* AI provider failure.
* Database failure.
* Vector search failure.
* Client API failure.
* Tool timeout.
* Invalid tool response.
* Authentication failure.
* Rate limiting.

Example:

```text
Client API unavailable
        ↓
Tool execution fails
        ↓
AI does NOT invent result
        ↓
Tell customer system is temporarily unavailable
        ↓
Offer human escalation
```

---

# 34. Docker Deployment

The platform must be packaged as a Docker image.

```text
Source Code
    ↓
Maven Build
    ↓
Spring Boot JAR
    ↓
Docker Image
    ↓
Cloud Platform
```

Configuration must use environment variables:

```text
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
AI_API_KEY
VECTOR_DB_CONFIGURATION
```

---

# 35. MVP

The first implementation should contain:

## Backend

* Spring Boot.
* Service-tier architecture.
* REST API.
* JWT/API-key security.
* Oracle database.
* Client management.
* Conversation management.

## AI

* Spring AI.
* LLM integration.
* RAG.
* Oracle Vector Search.
* Conversation memory.
* Basic tool calling.

## Agent

* Support queue.
* Agent assignment.
* Agent workspace.
* Internal notes.
* AI response suggestions.
* Conversation resolution.

## Integration

* REST API.
* One demo Spring Boot client.
* Client-specific tools.

## Deployment

* Docker.
* GitHub.
* Cloud deployment.

---

# 36. Demo Application

The first integration should be **BookVault**.

```text
BookVault
    ↓
AI Support Hub
```

Example:

Customer:

> "Is Clean Code available?"

AI Support Hub:

```text
1. Understand question
2. Call BookVault book API
3. Receive availability
4. Generate response
```

This demonstrates that the support platform can work with a real external Spring Boot application.

A second small demo application can later demonstrate multi-client support.

---

# 37. Example End-to-End Flow

```text
Customer
   ↓
BookVault
   ↓
POST /api/v1/support/chat
   ↓
AI Support Hub
   ↓
SupportService
   ↓
ConversationService
   ↓
AI Agent
   ├── RAG
   ├── Memory
   └── Tool Calling
           ↓
      BookVault API
           ↓
       Result
           ↓
      AI Response
           ↓
      BookVault
           ↓
       Customer
```

If AI cannot resolve the issue:

```text
AI
 ↓
EscalationService
 ↓
Support Queue
 ↓
Human Agent
 ↓
Customer
```

---

# 38. Future Development

## Phase 2

* JavaScript chat widget.
* Streaming responses.
* Automatic ticket classification.
* Automatic agent assignment.
* Webhooks.
* Email support.

## Phase 3

* Spring Boot Starter.
* Slack/Teams integration.
* Advanced analytics.
* Multiple AI providers.
* Voice support.
* Customer satisfaction tracking.

---

# 39. Success Criteria

The MVP is successful when:

1. A Spring Boot application can integrate with AI Support Hub using REST.
2. A customer can start a support conversation.
3. AI can answer client-specific knowledge questions.
4. AI can retrieve real-time information from the client application.
5. AI can use registered tools.
6. Sensitive actions require authorization.
7. AI can escalate conversations to human agents.
8. Agents can manage escalated conversations.
9. Conversations and actions are persisted.
10. Client data remains isolated.
11. The complete system runs through Docker.
12. The deployed API can be accessed by an external application.

---

# 40. Portfolio Positioning

The project should be presented as:

> **AI Support Hub is a reusable, multi-tenant AI customer-support platform built with Spring Boot and Spring AI. It provides REST-based integration for external applications and combines RAG, conversation memory, tool calling, real-time application data, human escalation, controlled business actions, and audit logging.**

The key architectural idea is:

```text
        Any Application
               │
               │ REST
               ▼
       ┌─────────────────┐
       │ AI Support Hub  │
       │                 │
       │ Spring Boot     │
       │ Spring AI       │
       │ Oracle          │
       │ Vector Search   │
       └─────────────────┘
```

**The client application does not need to know how the AI works. It only consumes the Support Hub API.**
