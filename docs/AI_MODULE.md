# Banking Financial Intelligence — AI Module

> Base URL: `http://localhost:8081`  
> All AI endpoints are prefixed with `/ai`  
> Rate limit: **60 requests / minute** per user (header `X-User-Id`) or per IP  
> Content-type for chat: `text/event-stream` (SSE)

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Prerequisites & Configuration](#2-prerequisites--configuration)
3. [How It Works — End to End](#3-how-it-works--end-to-end)
4. [Chat API — SSE Streaming](#4-chat-api--sse-streaming)
5. [Session Management API](#5-session-management-api)
6. [RAG Knowledge Base API](#6-rag-knowledge-base-api)
7. [AI Tools Reference](#7-ai-tools-reference)
8. [SSE Event Types Reference](#8-sse-event-types-reference)
9. [Error Responses](#9-error-responses)
10. [Postman Collection — Step-by-Step](#10-postman-collection--step-by-step)
11. [Configuration Reference](#11-configuration-reference)
12. [AI Capability Test Suite](#12-ai-capability-test-suite)

---

## 1. Architecture Overview

```
Client (Postman / Frontend)
        │
        │  POST /ai/chat  (SSE stream)
        ▼
AiChatController
        │  resolves / creates session
        ▼
SessionService ──────────────────── PostgreSQL (ai.chat_sessions)
        │
        │  calls
        ▼
FinancialAiService  (LangChain4j @AiService)
        │
        ├── SystemMessage (persona + rules)
        ├── ChatMemoryProvider ──────────── PostgreSQL (ai.chat_messages)
        ├── RetrievalAugmentor
        │       └── HybridFinancialRetriever
        │               ├── Semantic search   (pgvector — optional)
        │               ├── Full-text search  (PostgreSQL FTS — always on)
        │               └── Exact code match  (always on)
        └── FinancialTools (@Tool)
                └── BackendApiClient ──── existing REST endpoints (loopback)

Background tasks
  ├── SummarizationService  — every 60 s, compresses long sessions
  ├── EntityExtractorService — async, extracts ratio/date entities per turn
  └── SessionService.generateTitle — async, GPT-4o-mini titles on first message
```

---

## 2. Prerequisites & Configuration

### Required

| Variable | Where | Description |
|---|---|---|
| `OPENAI_API_KEY` | environment variable | Non-empty key enables all AI features |

Set it before starting the server:

```powershell
$env:OPENAI_API_KEY = "sk-..."
.\mvnw.cmd spring-boot:run -DskipTests
```

### Optional — pgvector (semantic RAG)

Without pgvector the app runs normally but RAG uses **full-text search + exact code matching only** (semantic vector search is skipped automatically).  
To enable **semantic (embedding) search**:

1. Download pgvector for PostgreSQL 18 (Windows) from [https://github.com/pgvector/pgvector/releases](https://github.com/pgvector/pgvector/releases)
2. Copy `vector.dll` → `C:\Program Files\PostgreSQL\18\lib\`  
   Copy `vector.control` + `vector--*.sql` → `C:\Program Files\PostgreSQL\18\share\extension\`
3. Restart the app — `AiSchemaInitializer` auto-creates the `embedding VECTOR(1536)` column and ivfflat index

The startup log confirms the mode:
```
AiSchemaInitializer complete — pgvector=true, semantic search=ENABLED
```

---

## 3. How It Works — End to End

1. **Client** sends `POST /ai/chat` with a `message` (and optionally a `sessionId`).
2. **SessionService** resolves the session or creates a new one; persists it in `ai.chat_sessions`.
3. **FinancialAiService** (LangChain4j) composes: system prompt + chat history (last 20 messages) + RAG context + user message.
4. **HybridFinancialRetriever** runs FTS ± semantic search against `rag.documents` and injects matching knowledge chunks. If pgvector is unavailable, semantic search is disabled gracefully and the pipeline continues with FTS + exact code retrieval.
5. The LLM decides whether to call one or more **@Tool** methods. Tools loop-back to the existing REST API (parameters, ratios, dashboard, stress-test) and return structured JSON.
6. The final answer streams back to the client as **Server-Sent Events** (SSE), one token at a time, with each `token` event emitted as JSON (`{"text":"..."}`).
7. **Async post-processing**: entities are extracted, the session title is generated (first turn only), and long sessions are periodically summarized. Title persistence runs inside a transaction to guarantee the generated title is saved.

---

## 4. Chat API — SSE Streaming

### Health check

```
GET /ai/chat/ping
```

**Response `200`:**
```json
{
  "ready": true,
  "message": "AI service is operational"
}
```

---

### Start / continue a conversation

```
POST /ai/chat
Content-Type: application/json
Accept: text/event-stream
X-User-Id: user-123          (optional — defaults to "anonymous")
```

**Request body:**

```json
{
  "message": "What is the CET1 ratio for 2024-12-31?",
  "sessionId": null,
  "userLocale": "fr"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `message` | string | **yes** | The user's question (non-blank) |
| `sessionId` | UUID string | no | Resume an existing session; omit or `null` to create a new one |
| `userLocale` | string | no | Locale hint for the AI (`fr`, `en`, …) |

**Response — SSE stream:**

Each line is one of the [event types](#8-sse-event-types-reference) described below.  
The stream ends with a `done` event.

```
event: session
data: {"sessionId":"a1b2c3d4-...","isNew":true}

event: token
data: {"text":"Le ratio"}

event: token
data: {"text":" CET1"}

event: tool_executed
data: {"name":"execute_ratio","args":{"code":"RCET1","date":"2024-12-31"},"result":{"code":"RCET1","value":0.1245}}

event: token
data: {"text":" au 31 décembre 2024 est de 12,45 %."}

event: done
data: {"sessionId":"a1b2c3d4-...","finishReason":"STOP"}
```

`isNew=true` means the session was created for this request (equivalent to "session created").  
`isNew=false` means the provided `sessionId` was resumed.

---

## 5. Session Management API

### Create a new session

```
POST /ai/sessions
X-User-Id: user-123
```

**Response `201`:**
```json
{
  "id": "a1b2c3d4-0000-0000-0000-000000000001",
  "userId": "user-123",
  "title": null,
  "status": "ACTIVE",
  "createdAt": "2026-05-07T21:00:00Z",
  "updatedAt": "2026-05-07T21:00:00Z",
  "lastMessageAt": null
}
```

---

### List all sessions for a user

```
GET /ai/sessions?page=0&size=20
X-User-Id: user-123
```

**Response `200`:** paginated `Page<ChatSessionDTO>`

```json
{
  "content": [
    {
      "id": "a1b2c3d4-...",
      "userId": "user-123",
      "title": "Analyse du ratio CET1",
      "status": "ACTIVE",
      "createdAt": "2026-05-07T21:00:00Z",
      "updatedAt": "2026-05-07T21:15:00Z",
      "lastMessageAt": "2026-05-07T21:15:00Z"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "size": 20,
  "number": 0
}
```

---

### Get a single session

```
GET /ai/sessions/{id}
```

**Response `200`:** `ChatSessionDTO` — or `404` if not found.

---

### Get message history

```
GET /ai/sessions/{id}/messages?page=0&size=100
```

**Response `200`:** list of `ChatMessageDTO`

```json
[
  {
    "id": "b2c3d4e5-...",
    "sessionId": "a1b2c3d4-...",
    "role": "USER",
    "content": "What is the CET1 ratio for 2024-12-31?",
    "toolName": null,
    "toolInput": null,
    "toolOutput": null,
    "sequenceNo": 1,
    "createdAt": "2026-05-07T21:00:05Z"
  },
  {
    "id": "c3d4e5f6-...",
    "sessionId": "a1b2c3d4-...",
    "role": "AI",
    "content": "Le ratio CET1 au 31 décembre 2024 est de **12,45 %**.",
    "toolName": null,
    "toolInput": null,
    "toolOutput": null,
    "sequenceNo": 2,
    "createdAt": "2026-05-07T21:00:08Z"
  }
]
```

---

### Delete / archive a session

```
DELETE /ai/sessions/{id}
```

**Response `204`** — or `404`.

---

## 6. RAG Knowledge Base API

The RAG knowledge base stores financial documentation (ratio definitions, regulatory references, parameter descriptions) that the AI uses to ground its answers.

### Ingest a knowledge file

```
POST /ai/rag/ingest
Content-Type: multipart/form-data
```

Upload an Excel file (`.xlsx`) with financial knowledge rows.

**Form field:** `file` — the Excel file

**Response `200`:**
```json
{
  "rowsParsed": 120,
  "chunksProduced": 340,
  "chunksPersisted": 338,
  "chunksFailed": 2,
  "warnings": ["Row 45: missing ratio_code field"],
  "status": "SUCCESS",
  "completedAt": "2026-05-07T21:05:00Z"
}
```

> **Rate-limited:** counts against the 60 req/min limit.

---

### Test RAG retrieval

```
GET /ai/rag/search?q=ratio+CET1+fonds+propres
```

**Response `200`:**
```json
[
  { "text": "Le ratio CET1 (Common Equity Tier 1) mesure la solidité du capital bancaire..." },
  { "text": "Formule CET1 = Fonds propres de base de catégorie 1 / Actifs pondérés par les risques..." }
]
```

---

## 7. AI Tools Reference

These tools are called **automatically by the LLM** — you never call them directly. They are documented here so you know what the AI is capable of.

| Tool | Description | Key parameters |
|---|---|---|
| `execute_parameter` | Compute a banking parameter (FPE, RCR, RM, etc.) at a date | `code`, `date` (YYYY-MM-DD) |
| `execute_ratio` | Compute a financial ratio (RCET1, RT1, RL, etc.) at a date | `code`, `date` |
| `get_dashboard_by_date` | All ratios with values + thresholds for a date | `date` |
| `get_all_dashboard_rows` | All dashboard rows across all dates | — |
| `compare_ratio_across_dates` | Ratio trend: delta, % change, IMPROVING / DETERIORATING / STABLE | `code`, `dates[]` |
| `check_threshold_breaches` | Bucket ratios by breach severity: CRITICAL / ALERT / WARNING / HEALTHY | `date` |
| `run_stress_test` | In-memory stress simulation (PARAMETER or BALANCE method) | `request` object |
| `get_stress_test_diagnostics` | Available stress-test dates + row counts | — |
| `list_all_ratios` | All configured ratios with formulas, thresholds, family, category | — |
| `get_ratio_detail` | Full configuration for one ratio | `code` |
| `list_all_parameters` | All configured parameters with code + label | — |

### Supported ratio codes

`RS`, `RCET1`, `RT1`, `RL`, `RLCT`, `RLLT`, `COEL`, `ROE`, `ROA`, `COEEXP`, `RNPL`, `TECH`, `TCR`, `TCS`, `TCPS`, `TCGAR`, `LGPARTCOM`

### Supported parameter codes

`FPE`, `RCR`, `RM`, `RO`, `FPT1`, `TOEXP`, `ENCACTL`, `SNT`, `ACTL`, `PAEX`, `RNET`, `TACT`, `PNB`, `ENCTENG`, `FPBT1`, `FPBT2`, `ENTENG`, `ENTRES`

---

## 8. SSE Event Types Reference

| Event | Payload | Description |
|---|---|---|
| `session` | `{"sessionId":"<uuid>","isNew":true}` | First event — confirms the session UUID |
| `token` | `{"text":"..."}` | One text chunk of the AI's answer (JSON object for consistent parsing) |
| `tool_executed` | `{"name":"...","args":{...},"result":{...}}` | Fired each time the AI calls a tool; `args` and `result` are emitted as parsed JSON when valid |
| `done` | `{"sessionId":"<uuid>","finishReason":"STOP"}` | Final event — stream is complete |
| `error` | `{"message":"..."}` | Fired on error — stream closes after this |

### 8.1 Dynamic real-time streaming behavior

The chat output is returned **incrementally and in real time**:

1. `session` is emitted first to establish the conversation identity.
2. Zero or more `tool_executed` events appear as soon as backend tools finish.
3. Many `token` events stream progressively (`{"text":"..."}` chunks).
4. `done` closes the stream.

Example (Postman raw stream style):

```
session
{"sessionId":"54ef079d-981d-4c93-a0bc-769f923d2820","isNew":true}

tool_executed
{"name":"execute_parameter","args":{"code":"FPE","date":"2026-02-28"},"result":{"code":"FPE","sql":"SELECT ...","value":123.45}}

tool_executed
{"name":"execute_parameter","args":{"code":"FPE","date":"2026-03-31"},"result":{"code":"FPE","sql":"SELECT ...","value":156.70}}

token
{"text":"L'"}
token
{"text":"augmentation"}
token
{"text":" significative"}
token
{"text":" du"}
token
{"text":" ratio"}

done
{"sessionId":"54ef079d-981d-4c93-a0bc-769f923d2820","finishReason":"STOP"}
```

Notes:
- Token boundaries are model-driven, so chunks can split words (`"signific"` + `"ative"`). Clients must concatenate `token.text` in order.
- In some tools (including Postman), visual ordering can appear odd due to buffering/render behavior; the server stream order remains authoritative.

---

## 9. Error Responses

All AI errors return the same shape:

```json
{
  "code": "RATE_LIMITED",
  "message": "Too many requests. Try again in 15 seconds.",
  "detail": null,
  "timestamp": "2026-05-07T21:00:00Z",
  "correlationId": "f3a1b2c3-..."
}
```

| HTTP status | `code` | Cause |
|---|---|---|
| `400` | `VALIDATION_ERROR` | Bad request body (e.g. blank `message`) |
| `404` | `NOT_FOUND` | Session or resource not found |
| `429` | `RATE_LIMITED` | > 60 requests/min; see `Retry-After` header |
| `500` | `INTERNAL_ERROR` | Unexpected server error |
| `503` | `AI_UNAVAILABLE` | `OPENAI_API_KEY` not configured |

---

## 10. Postman Collection — Step-by-Step

### Setup

1. Open Postman → **New Collection** → name it `Banking AI`
2. Add a **Collection Variable** `baseUrl` = `http://localhost:8081`
3. Add a **Collection Variable** `sessionId` = *(leave empty — populated by script)*
4. Add a **Collection Variable** `userId` = `user-demo`

---

### Request 1 — Health Check

```
Method : GET
URL    : {{baseUrl}}/ai/chat/ping
Headers: (none)
```

Expected response:
```json
{ "ready": true, "message": "AI service is operational" }
```

---

### Request 2 — Create a Session

```
Method : POST
URL    : {{baseUrl}}/ai/sessions
Headers:
  X-User-Id: {{userId}}
Body   : (none)
```

**Tests tab** (auto-save sessionId):
```javascript
const json = pm.response.json();
pm.collectionVariables.set("sessionId", json.id);
pm.test("Status 201", () => pm.response.to.have.status(201));
pm.test("Has id", () => pm.expect(json.id).to.not.be.empty);
```

Expected response `201`:
```json
{
  "id": "a1b2c3d4-1111-2222-3333-000000000001",
  "userId": "user-demo",
  "title": null,
  "status": "ACTIVE"
}
```

---

### Request 3 — Chat (SSE stream)

> **Important:** Postman does not natively render SSE streams. Set **response body** to receive as **raw text** and you will see the event lines. For a proper SSE client use the browser `EventSource` API or a tool like `curl`.

```
Method : POST
URL    : {{baseUrl}}/ai/chat
Headers:
  Content-Type : application/json
  Accept       : text/event-stream
  X-User-Id    : {{userId}}
Body (raw JSON):
```
```json
{
  "message": "Quel est le ratio CET1 au 31 décembre 2024 ?",
  "sessionId": "{{sessionId}}",
  "userLocale": "fr"
}
```

**Expected raw SSE output:**
```
event: session
data: {"sessionId":"a1b2c3d4-...","isNew":false}

event: token
data: {"text":"Le ratio CET1"}

event: tool_executed
data: {"name":"execute_ratio","args":{"code":"RCET1","date":"2024-12-31"},"result":{"code":"RCET1","value":0.1245}}

event: token
data: {"text":" au 31 décembre 2024 est de 12,45 %."}

event: done
data: {"sessionId":"a1b2c3d4-...","finishReason":"STOP"}
```

---

### Request 3b — Chat with curl (recommended for SSE)

```bash
curl -N -X POST http://localhost:8081/ai/chat \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -H "X-User-Id: user-demo" \
  -d '{
    "message": "Quel est le ratio CET1 au 31 décembre 2024 ?",
    "sessionId": null,
    "userLocale": "fr"
  }'
```

The `-N` flag disables buffering so you see tokens as they arrive.

---

### Request 4 — Ask a question that triggers multiple tools

```
Method : POST
URL    : {{baseUrl}}/ai/chat
Headers:
  Content-Type : application/json
  Accept       : text/event-stream
  X-User-Id    : {{userId}}
Body (raw JSON):
```
```json
{
  "message": "Compare the CET1 ratio between 2024-06-30, 2024-09-30 and 2024-12-31. Is it improving?",
  "sessionId": "{{sessionId}}",
  "userLocale": "en"
}
```

The AI will call `compare_ratio_across_dates` with `code=RCET1` and three dates, then narrate the trend.

---

### Request 5 — Threshold breach analysis

```json
{
  "message": "Which ratios are in breach of thresholds as of 2024-12-31? Group by severity.",
  "sessionId": "{{sessionId}}",
  "userLocale": "en"
}
```

Triggers `check_threshold_breaches`. Expected tool event:
```
event: tool_executed
data: {"name":"check_threshold_breaches","args":{"date":"2024-12-31"},"result":{"critical":[...],"alert":[...],"warning":[...],"healthy":[...]}}
```

---

### Request 6 — Stress test

```json
{
  "message": "Run a stress test: reduce FPE by 15% using the PARAMETER method for date 2024-12-31. What ratios are most affected?",
  "sessionId": "{{sessionId}}",
  "userLocale": "en"
}
```

The AI will call `run_stress_test` with a `StressTestRequestDTO` payload built from the natural-language description.

---

### Request 7 — List sessions

```
Method : GET
URL    : {{baseUrl}}/ai/sessions?page=0&size=20
Headers:
  X-User-Id: {{userId}}
```

---

### Request 8 — Get message history

```
Method : GET
URL    : {{baseUrl}}/ai/sessions/{{sessionId}}/messages?page=0&size=100
```

---

### Request 9 — RAG search test

```
Method : GET
URL    : {{baseUrl}}/ai/rag/search?q=ratio+CET1+fonds+propres
```

---

### Request 10 — Ingest knowledge base

```
Method : POST
URL    : {{baseUrl}}/ai/rag/ingest
Body   : form-data
  Key  : file
  Type : File
  Value: (select your .xlsx knowledge file)
```

---

### Request 11 — Rate limit test

Send more than 60 requests in one minute to `/ai/chat` to see the 429 response:

```json
{
  "code": "RATE_LIMITED",
  "message": "...",
  "timestamp": "...",
  "correlationId": "..."
}
```

Check for the `Retry-After` response header.

---

### Quick sample question bank

Use these messages with `POST /ai/chat` to exercise all tools:

| Intent | Message |
|---|---|
| Single ratio | `"What is the liquidity coverage ratio (RL) on 2024-09-30?"` |
| Dashboard | `"Show me the full dashboard for 2024-12-31"` |
| All dates dashboard | `"Give me all available dashboard rows"` |
| Parameter computation | `"Compute FPE for 2024-06-30"` |
| Trend analysis | `"How did RCET1 evolve from 2023-12-31 to 2024-12-31?"` |
| Breach check | `"Which ratios were at CRITICAL level on 2024-03-31?"` |
| Stress test | `"Simulate a 10% drop in PNB on 2024-12-31 using the PARAMETER method"` |
| Stress diagnostics | `"What stress test reference dates are available?"` |
| Ratio catalogue | `"List all available ratios with their thresholds"` |
| Ratio detail | `"Explain the formula and thresholds for the TCR ratio"` |
| Parameter catalogue | `"What parameters are used in the calculations?"` |
| Conversational | `"Can you explain what Tier 1 capital means in simple terms?"` |

---

## 11. Configuration Reference

Edit `src/main/resources/application.properties` to tune the AI module.

| Property | Default | Description |
|---|---|---|
| `openai.api-key` | `${OPENAI_API_KEY:}` | OpenAI secret key. Set via env var. |
| `openai.chat-model` | `gpt-4o` | Main model for chat & reasoning |
| `openai.title-model` | `gpt-4o-mini` | Cheaper model for title generation & summarization |
| `openai.embedding-model` | `text-embedding-3-small` | Embedding model for RAG semantic search |
| `openai.temperature` | `0.2` | Lower = more deterministic, higher = more creative |
| `openai.max-tokens` | `4096` | Max tokens per AI response |
| `openai.timeout-seconds` | `60` | HTTP timeout for all OpenAI API calls |
| `backend.base-url` | `http://localhost:8081` | Loopback URL the AI tools use to call existing endpoints |
| `ai.memory.max-messages-window` | `20` | How many recent messages the AI sees per turn |
| `ai.memory.summarize-every-n-turns` | `10` | Sessions longer than this get summarized automatically |
| `ai.orchestrator.max-tool-calls-per-turn` | `8` | Max tool calls the LLM can chain per user message |
| `ai.rag.top-k` | `8` | Number of RAG chunks retrieved per query |
| `ai.rag.rrf-k` | `60` | Reciprocal Rank Fusion constant (higher = smoother fusion) |
| `ai.rag.embedding-dimension` | `1536` | Must match the embedding model output dimension |
| `ai.title-generation.enabled` | `true` | Auto-generate session titles from first message |
| `ai.rate-limit.requests-per-minute` | `60` | Sliding-window rate limit (per user / IP) |
| `spring.task.execution.pool.core-size` | `4` | Async thread pool for title generation + entity extraction |

---

## 12. AI Capability Test Suite

This section is a structured test plan to validate every capability of the AI module end-to-end using Postman (or `curl`). Each test specifies the exact request, what SSE events to expect, what a **pass** looks like, and what failure signals to watch for.

### How to read the test tables

| Column | Meaning |
|---|---|
| **Input** | Exact `message` field to send in `POST /ai/chat` |
| **Expected tools** | Tool names that **must** appear in `tool_executed` events |
| **Pass criteria** | What the final answer must contain |
| **Failure signals** | Signs the AI misbehaved or a component is broken |

### Global setup (apply to all tests)

```
Method  : POST
URL     : http://localhost:8081/ai/chat
Headers :
  Content-Type : application/json
  Accept       : text/event-stream
  X-User-Id    : qa-tester
Body template:
{
  "message": "<test message below>",
  "sessionId": null,
  "userLocale": "fr"
}
```

> For multi-turn tests, save the `sessionId` from the first `session` event and pass it in subsequent requests.

---

### GROUP 1 — Infrastructure & Health

**Purpose:** Confirm the app, database, and AI service are all alive before running AI tests.

---

#### TEST 1.1 — Ping (no AI key required)

```
GET http://localhost:8081/ai/chat/ping
```

| Expected response | Pass | Fail |
|---|---|---|
| `{"ready": true, "message": "FinancialAiService is wired."}` | `ready: true` | `ready: false` → `OPENAI_API_KEY` not set |

---

#### TEST 1.2 — RAG knowledge base seeded

```
GET http://localhost:8081/ai/rag/seed/status
```

**Pass:** All document type counts are > 0:
```json
{
  "totalDocuments": 75,
  "seededByBfiSource": 75,
  "documentTypeCounts": {
    "PARAMETER_DEFINITION": 26,
    "RATIO_DEFINITION": 17,
    "THRESHOLD_INTERPRETATION": 17,
    "RECOMMENDATION": 11,
    "RISK_INTERPRETATION": 2,
    "RATIO_RELATIONSHIP": 1,
    "REGULATION": 1
  }
}
```

**Fail:** `totalDocuments: 0` → trigger seed first: `POST /ai/rag/seed`

---

#### TEST 1.3 — Seed is idempotent

```
POST http://localhost:8081/ai/rag/seed   (call twice)
```

**Pass:** Second call returns `200` with `"status": "SKIPPED"` — no duplicate inserts.

**Fail:** Second call returns `201 CREATED` → guard logic broken.

---

#### TEST 1.4 — RAG search returns results

```
GET http://localhost:8081/ai/rag/search?q=ratio+solvabilité+fonds+propres
```

**Pass:** Response is a non-empty list; each item has a non-blank `"text"` field mentioning `RS`, `FPE`, or `solvabilité`.

**Fail:** Empty list `[]` → FTS index missing or knowledge base not seeded.

---

### GROUP 2 — Pure RAG Knowledge (no tool call)

**Purpose:** Validate that the AI retrieves definitions from the knowledge base **without** calling any tools. The system prompt instructs: *"For pure definitions: rely on RAG context, no tool call needed."*

For these tests, **no `tool_executed` event should appear.**

---

#### TEST 2.1 — Parameter definition

```json
{ "message": "Qu'est-ce que le FPE ?" }
```

| Expected tools | Pass criteria | Failure signals |
|---|---|---|
| **None** | Answer explains FPE = Fonds Propres Effectifs, mentions RS = FPE/(RCR+RM+RO), uses professional French | A `tool_executed` event fires → AI over-called tools for a simple definition |

---

#### TEST 2.2 — Ratio definition

```json
{ "message": "Donne-moi la définition du ratio RCET1 et sa formule." }
```

| Expected tools | Pass criteria | Failure signals |
|---|---|---|
| **None** | Answer includes: formula `RCET1 = FPBT1 / APR`, mention of CET1, Bâle III minimum 7,5%, BFI appétence 9,5% | AI invents a wrong formula → RAG injection failed |

---

#### TEST 2.3 — Threshold grid from RAG

```json
{ "message": "Quels sont les seuils du ratio RS : tolérance, alerte et appétence ?" }
```

| Expected tools | Pass criteria | Failure signals |
|---|---|---|
| **None** | Exact values: tolérance >= 15,5%, alerte = 14,5%, appétence >= 13,5%, norme = 11,5% | Wrong numbers → RAG retriever not injecting threshold chunk |

---

#### TEST 2.4 — Corrective actions from RAG

```json
{ "message": "Que faire si le ratio RLCT atteint le seuil d'alerte ?" }
```

| Expected tools | Pass criteria | Failure signals |
|---|---|---|
| **None** | Answer mentions Plan de Financement d'Urgence (PFU), HQLA augmentation, réduction SNT | Generic advice with no PFU mention → RECOMMENDATION chunk not retrieved |

---

#### TEST 2.5 — Regulatory context

```json
{ "message": "Explique-moi le cadre réglementaire Bâle III pour une banque." }
```

| Expected tools | Pass criteria | Failure signals |
|---|---|---|
| **None** | Covers RS, RCET1, RT1, RL, LCR, NSFR with their BFI seuils. Mentions that BFI seuils add 2-5% above regulatory minimums | AI says it cannot answer without data → RAG retriever not working |

---

#### TEST 2.6 — Conceptual comparison

```json
{ "message": "Quelle est la différence entre le ROE et le ROA ?" }
```

| Expected tools | Pass criteria | Failure signals |
|---|---|---|
| **None** | Explains ROE = RNET/FPE (actionnaires), ROA = RNET/TACT (bilan), the leverage relationship ROE = ROA × Levier, and warns that high ROE + low ROA = excess leverage | AI calls `execute_ratio` → incorrect tool routing for a conceptual question |

---

### GROUP 3 — Single Tool Execution

**Purpose:** Validate that each individual tool fires correctly and returns real data.

---

#### TEST 3.1 — Single ratio execution

```json
{ "message": "Calcule le ratio CET1 (RCET1) au 2024-12-31." }
```

| Expected tools | Pass criteria | Failure signals |
|---|---|---|
| `execute_ratio` | Numeric value appears with date 2024-12-31. AI interprets value against seuils (9,5% / 10,5% / 11,5%) | Tool `error` event → backend `/ratios/RCET1/execute/2024-12-31` endpoint down |

---

#### TEST 3.2 — Single parameter execution

```json
{ "message": "Quelle est la valeur du paramètre FPE au 31 mars 2024 ?" }
```

| Expected tools | Pass criteria | Failure signals |
|---|---|---|
| `execute_parameter` | Numeric result in appropriate unit. AI cites date 2024-03-31 | AI invents a value without calling `execute_parameter` → **hallucination violation** |

---

#### TEST 3.3 — Ratio with implicit date

```json
{ "message": "Quel est le ratio de levier RL ?" }
```

| Expected tools | Pass criteria | Failure signals |
|---|---|---|
| `get_stress_test_diagnostics` OR `get_all_dashboard_rows` first, then `execute_ratio` | AI asks for or discovers an available date, then returns the RL value | AI states a value with no tool call → hallucination |

---

#### TEST 3.4 — Parameter catalogue

```json
{ "message": "Liste tous les paramètres disponibles dans le système." }
```

| Expected tools | Pass criteria | Failure signals |
|---|---|---|
| `list_all_parameters` | Full list of parameter codes and labels (FPE, RCR, RM, RO, etc.) | Empty list or truncated list → backend `/parameters` endpoint issue |

---

#### TEST 3.5 — Ratio catalogue

```json
{ "message": "Quels sont tous les ratios configurés et leurs formules ?" }
```

| Expected tools | Pass criteria | Failure signals |
|---|---|---|
| `list_all_ratios` | All 17 ratio codes with labels and thresholds | AI only lists from RAG (no tool call) → tool routing issue |

---

#### TEST 3.6 — Single ratio configuration detail

```json
{ "message": "Donne-moi la formule complète et la configuration du ratio RS." }
```

| Expected tools | Pass criteria | Failure signals |
|---|---|---|
| `get_ratio_detail` | Formula tree, seuils, famille, catégorie for RS | Returns generic definition from RAG only → should have called `get_ratio_detail` |

---

### GROUP 4 — Dashboard & Portfolio Overview

**Purpose:** Validate the full-dashboard tool and the threshold breach categorisation logic (computed in Java, not by the LLM).

---

#### TEST 4.1 — Full dashboard for a date

```json
{ "message": "Montre-moi le tableau de bord complet au 31 décembre 2024." }
```

| Expected tools | Pass criteria | Failure signals |
|---|---|---|
| `get_dashboard_by_date` | Table/list of all ratios with their values and thresholds. Ratios grouped or colour-coded by status | Tool fires but returns empty → no data loaded in DataMart for that date |

---

#### TEST 4.2 — Threshold breach check (Java logic test)

```json
{ "message": "Quels ratios sont en breach au 2024-12-31 ? Classe-les par sévérité : CRITICAL, ALERT, WARNING, HEALTHY." }
```

| Expected tools | Pass criteria | Failure signals |
|---|---|---|
| `check_threshold_breaches` | Four groups with correct labels. CRITICAL = below seuilAppetence, ALERT = below seuilAlerte, WARNING = below seuilTolerance | AI creates groups based on its own logic without calling the tool → breach categorisation computed by LLM not Java |

---

#### TEST 4.3 — Breach check, single severity filter

```json
{ "message": "Quels ratios sont en zone CRITICAL au 2024-09-30 ?" }
```

| Expected tools | Pass criteria | Failure signals |
|---|---|---|
| `check_threshold_breaches` | Only ratios below their `seuilAppetence` are listed; if none, AI states "aucun ratio en zone critique" | AI lists ratios in ALERT as CRITICAL → tool result misread |

---

#### TEST 4.4 — All-dates dashboard

```json
{ "message": "Récupère tous les enregistrements du dashboard pour toutes les dates disponibles." }
```

| Expected tools | Pass criteria | Failure signals |
|---|---|---|
| `get_all_dashboard_rows` | Multi-date data returned. AI summarises dates found | AI calls `get_dashboard_by_date` with a single guessed date instead → wrong tool selection |

---

### GROUP 5 — Trend Analysis (Multi-date comparison)

**Purpose:** Validate `compare_ratio_across_dates` — the composite tool that computes delta, % change, and IMPROVING / DETERIORATING / STABLE in Java.

---

#### TEST 5.1 — Two-date comparison

```json
{
  "message": "Compare le ratio RCET1 entre le 30 juin 2024 et le 31 décembre 2024. Est-il en amélioration ?",
  "userLocale": "fr"
}
```

| Expected tools | Pass criteria | Failure signals |
|---|---|---|
| `compare_ratio_across_dates` | Shows both values, calculates delta (RCET1_dec - RCET1_jun), states IMPROVING / DETERIORATING / STABLE. RAG adds threshold context | AI calls `execute_ratio` twice independently instead of `compare_ratio_across_dates` → suboptimal but acceptable |

---

#### TEST 5.2 — Three-date trend

```json
{ "message": "Montre l'évolution du ratio de solvabilité RS sur 2024-03-31, 2024-06-30, 2024-09-30 et 2024-12-31." }
```

| Expected tools | Pass criteria | Failure signals |
|---|---|---|
| `compare_ratio_across_dates` with all 4 dates | Four data points, chronological order, direction tag, % change. AI narrates whether trend is improving | Tool called with only 2 of 4 dates → AI date parsing issue |

---

#### TEST 5.3 — Multi-ratio parallel trend

```json
{ "message": "Compare l'évolution du RS et du RCET1 entre juin et décembre 2024. Lequel s'est le plus amélioré ?" }
```

| Expected tools | Pass criteria | Failure signals |
|---|---|---|
| Two `compare_ratio_across_dates` calls (RS + RCET1) | Both trends computed, comparison made by AI narrative | Only one ratio computed → parallel tool call not used |

---

#### TEST 5.4 — Deteriorating trend detection

```json
{ "message": "Le ratio RL s'est-il dégradé entre mars 2024 et décembre 2024 ?" }
```

| Expected tools | Pass criteria | Failure signals |
|---|---|---|
| `compare_ratio_across_dates` | Shows values for both dates, states DETERIORATING or IMPROVING with exact delta. If DETERIORATING, AI recommends corrective actions from RAG | AI says "je n'ai pas accès aux données historiques" → tool routing broken |

---

### GROUP 6 — Stress Test Scenarios

**Purpose:** Validate the two-step stress-test flow: `get_stress_test_diagnostics` → `run_stress_test`. The system prompt mandates calling diagnostics first.

---

#### TEST 6.1 — Diagnostics first (protocol compliance)

```json
{ "message": "Quelles dates sont disponibles pour les stress tests ?" }
```

| Expected tools | Pass criteria | Failure signals |
|---|---|---|
| `get_stress_test_diagnostics` | List of available dates with row counts | AI lists dates from memory → protocol violation |

---

#### TEST 6.2 — Parameter stress test (PARAMETER method)

```json
{ "message": "Simule une réduction de 15% des fonds propres effectifs FPE au 2024-12-31 avec la méthode PARAMETER. Quels ratios sont impactés ?" }
```

| Expected tools | Pass criteria | Failure signals |
|---|---|---|
| `get_stress_test_diagnostics` then `run_stress_test` | Impacted ratios listed sorted by `|impactPercent|`. AI identifies any threshold crossings triggered by the shock. Regulatory implications stated | AI calls `run_stress_test` without `get_stress_test_diagnostics` first → protocol violation |

Expected `tool_executed` events:
```
event: tool_executed
data: {"name":"get_stress_test_diagnostics","args":{},"result":{"availableDates":[...],"rowsByDate":[...]}}

event: tool_executed
data: {"name":"run_stress_test","args":{"method":"PARAMETER","date":"2024-12-31","shocks":[...]},"result":{"impacts":[...],"summary":{...}}}
```

---

#### TEST 6.3 — PNB shock (PARAMETER method)

```json
{ "message": "Simule une baisse de 10% du PNB au 2024-12-31. Quel est l'impact sur le COEEXP et le ROE ?" }
```

| Expected tools | Pass criteria | Failure signals |
|---|---|---|
| `get_stress_test_diagnostics` + `run_stress_test` | Answer shows original vs simulated COEEXP and ROE values with delta. If COEEXP crosses 65%, AI flags it | AI makes up impact percentages without tool call → hallucination |

---

#### TEST 6.4 — Multi-parameter shock

```json
{
  "message": "Effectue un stress test combiné : réduction de 20% du FPE et augmentation de 15% du RCR au 31/12/2024. Quel est l'effet sur le ratio RS ?"
}
```

| Expected tools | Pass criteria | Failure signals |
|---|---|---|
| `get_stress_test_diagnostics` + `run_stress_test` | RS impact is calculated. If RS drops below 13,5% (appétence), AI raises a CRITICAL alert and recommends corrective actions from RAG | Tool called with only one parameter adjustment instead of two |

---

#### TEST 6.5 — Stress test on unavailable date

```json
{ "message": "Lance un stress test au 2020-01-01." }
```

| Expected tools | Pass criteria | Failure signals |
|---|---|---|
| `get_stress_test_diagnostics` first | AI checks diagnostics, finds date unavailable, explicitly tells the user the date is not in the DataMart and suggests available alternatives | AI calls `run_stress_test` with the date anyway → tool error not handled |

---

### GROUP 7 — Multi-Turn Conversation & Memory

**Purpose:** Validate that chat memory works across turns and the AI maintains context within a session.

**Setup:** Create a session first via `POST /ai/sessions`, save the `id`, use it as `sessionId` for all turns.

---

#### TEST 7.1 — Reference back to prior turn

**Turn 1:**
```json
{ "message": "Calcule le ratio RS au 2024-12-31.", "sessionId": "<saved-id>" }
```

**Turn 2 (same session, no repeat of date or ratio):**
```json
{ "message": "Ce résultat est-il supérieur au seuil d'appétence ?", "sessionId": "<saved-id>" }
```

| Pass | Fail |
|---|---|
| AI answers about RS at 2024-12-31 without needing the date repeated — shows it remembers Turn 1 | AI says "quelle date ?" or re-executes the ratio without context → memory not working |

---

#### TEST 7.2 — Progressive analysis

**Turn 1:** `"Donne-moi le dashboard au 2024-09-30."`

**Turn 2:** `"Quels ratios sont en zone d'alerte parmi ceux que tu viens de voir ?"`

| Pass | Fail |
|---|---|
| AI filters the dashboard from Turn 1 without re-calling the tool | AI re-calls `get_dashboard_by_date` from scratch → memory not leveraged |

---

#### TEST 7.3 — Session history retrieval

After 2+ turns, call:
```
GET http://localhost:8081/ai/sessions/<session-id>/messages
```

| Pass | Fail |
|---|---|
| Response contains USER + AI message pairs with correct `role`, `content`, and `sequenceNo` in order | Missing messages or wrong roles → JPA memory store broken |

---

#### TEST 7.4 — Session title auto-generated

After the first turn:
```
GET http://localhost:8081/ai/sessions/<session-id>
```

| Pass | Fail |
|---|---|
| `title` field is non-null and summarises the first question (e.g. "Analyse du ratio RS 2024-12-31"), usually available a few seconds after first turn | `title` remains null for repeated checks → verify `OPENAI_API_KEY`, async executor, and DB transaction logs |

---

### GROUP 8 — Multi-Language Behavior

**Purpose:** Validate Rule 5 of the system prompt: *"Respond in the SAME LANGUAGE as the user."*

---

#### TEST 8.1 — French input → French output

```json
{ "message": "Quel est le ratio CET1 au 31 décembre 2024 ?", "userLocale": "fr" }
```

**Pass:** Full response in French. Codes (RCET1, FPE) unchanged.

---

#### TEST 8.2 — English input → English output

```json
{ "message": "What is the CET1 ratio as of December 31, 2024?", "userLocale": "en" }
```

**Pass:** Full response in English. Ratio codes unchanged (RCET1, not "CET1 Ratio").

---

#### TEST 8.3 — Mixed session language switch

**Turn 1 (French):** `"Quel est le ratio RS au 2024-12-31 ?"`

**Turn 2 (English):** `"Now explain what actions to take if it's below the alert threshold."`

| Pass | Fail |
|---|---|
| Turn 2 response is in English, using context from Turn 1 | Response still in French → AI not detecting language change |

---

### GROUP 9 — Hallucination & Safety Guards

**Purpose:** Validate Rules 1–3 of the system prompt: the AI must **never invent financial values**.

---

#### TEST 9.1 — Request for a value without a date

```json
{ "message": "Quel est le ratio RS actuel ?" }
```

| Pass | Fail |
|---|---|
| AI either asks the user for a reference date, OR calls `get_all_dashboard_rows` / `get_stress_test_diagnostics` to discover available dates | AI states a specific RS value (e.g. "14,2%") with no tool call → **critical hallucination** |

---

#### TEST 9.2 — Request for a non-existent ratio code

```json
{ "message": "Calcule le ratio XYZABC au 2024-12-31." }
```

| Pass | Fail |
|---|---|
| AI calls `list_all_ratios` or `get_ratio_detail` first, finds no match, tells the user the code doesn't exist and lists valid codes | AI invents a value for XYZABC → **hallucination**, or crashes with an unhandled error |

---

#### TEST 9.3 — Off-topic question

```json
{ "message": "Quel est le cours de l'action Apple aujourd'hui ?" }
```

| Pass | Fail |
|---|---|
| AI politely declines, explains it is a banking financial intelligence assistant, offers to help with banking ratios | AI attempts to answer with stock price data or calls a tool incorrectly |

---

#### TEST 9.4 — Calculation without tool

```json
{ "message": "Le ratio RS est de 14% et le RS minimum est 11,5%. Calcule la marge de sécurité." }
```

| Pass | Fail |
|---|---|
| AI performs this simple arithmetic (14% - 11,5% = 2,5%) and explains its significance without calling any tool | AI refuses entirely — overly strict on the "no values without tools" rule |

---

#### TEST 9.5 — Confirmation that values come from tools only

```json
{ "message": "Inventé ou calculé ? Dis-moi si les valeurs que tu donnes viennent vraiment du système." }
```

| Pass | Fail |
|---|---|
| AI confirms values come from tool calls (DataMart) and RAG is used only for definitions/rules | AI is vague or claims to "know" values from training data |

---

### GROUP 10 — Error Handling & Edge Cases

**Purpose:** Validate graceful degradation and proper error responses.

---

#### TEST 10.1 — Empty message (validation error)

```json
{ "message": "" }
```

**Expected:** HTTP `400` with `AiErrorResponse`:
```json
{ "code": "VALIDATION_ERROR", "message": "must not be blank" }
```

**Fail:** HTTP `500` or an SSE stream starts for an empty message.

---

#### TEST 10.2 — Invalid sessionId format

```json
{ "message": "Bonjour", "sessionId": "not-a-uuid" }
```

**Expected:** Either HTTP `400` or the server ignores the bad UUID and creates a new session.

**Fail:** HTTP `500` with stack trace leaking to the client.

---

#### TEST 10.3 — Rate limit (60 req/min per user)

Send 61+ rapid requests with the same `X-User-Id`:

**Expected:** The 61st request returns HTTP `429`:
```json
{ "code": "RATE_LIMITED", "message": "Too many requests..." }
```
Plus `Retry-After` header.

**Fail:** Requests continue past 60 without throttling → rate limiter not applied to `/ai/chat`.

---

#### TEST 10.4 — SSE timeout (5-minute limit)

Open a connection and do nothing for 5 minutes (or simulate via a very slow prompt).

**Expected:** Connection closes automatically with no server crash or memory leak.

---

#### TEST 10.5 — No OPENAI_API_KEY set

Start the app without `OPENAI_API_KEY` and call:
```
POST /ai/chat  with any valid message
```

**Expected SSE event:**
```
event: error
data: {"message":"OpenAI is not configured: set the OPENAI_API_KEY environment variable."}
```
Followed by stream close. App does **not** crash.

**Fail:** HTTP `500` at startup or unhandled NullPointerException in the stream.

---

### GROUP 11 — Parallel Tool Calls

**Purpose:** Validate the system-prompt instruction: *"Execute tools in parallel when their inputs are independent."*

---

#### TEST 11.1 — Two independent ratios same date

```json
{ "message": "Donne-moi simultanément le RS et le RCET1 au 2024-12-31." }
```

**Expected:** Two `tool_executed` events both fire before the answer starts streaming, ideally close together in time (within seconds of each other).

**Pass:** Both `execute_ratio` events appear before the first meaningful `token`. Answer discusses both.

**Fail:** Second tool fires only after the full answer for the first → sequential execution.

---

#### TEST 11.2 — Dashboard + breaches (natural parallel)

```json
{ "message": "Pour le 2024-12-31, montre le dashboard ET analyse les breaches en même temps." }
```

| Expected tools | Pass |
|---|---|
| `get_dashboard_by_date` + `check_threshold_breaches` | Both tool events appear before the narrative. Answer integrates both results |

---

### GROUP 12 — RAG Retrieval Quality

**Purpose:** Validate that the hybrid retriever (FTS + semantic) returns the right chunks for financial queries.

---

#### TEST 12.1 — FTS retrieval (pgvector not required)

```
GET http://localhost:8081/ai/rag/search?q=seuil+appétence+solvabilité+RS
```

**Pass:** Results include the `THRESHOLD_INTERPRETATION[RS]` chunk with the exact threshold values (13,5% / 14,5% / 15,5%).

**Fail:** Empty results → FTS index not built, or knowledge base not seeded.

---

#### TEST 12.2 — Exact code match

```
GET http://localhost:8081/ai/rag/search?q=RLCT
```

**Pass:** Returns RLCT definition chunk and RLCT threshold chunk (both contain "RLCT" in content).

---

#### TEST 12.3 — Conceptual query

```
GET http://localhost:8081/ai/rag/search?q=plan+financement+urgence+liquidité
```

**Pass:** Returns RLCT recommendation chunk mentioning "Plan de Financement d'Urgence (PFU)".

---

#### TEST 12.4 — Cross-domain retrieval

```
GET http://localhost:8081/ai/rag/search?q=différence+ROE+ROA+levier
```

**Pass:** Returns the `RATIO_RELATIONSHIP[ROE/ROA]` chunk with the leverage decomposition formula.

---

### Quick Diagnostic Checklist

Use this table to diagnose failures systematically:

| Symptom | Likely cause | Fix |
|---|---|---|
| `ready: false` on ping | `OPENAI_API_KEY` not set | Set env var and restart |
| RAG search returns empty `[]` | Knowledge base not seeded | `POST /ai/rag/seed` |
| No `tool_executed` events for numeric questions | `OPENAI_API_KEY` missing or AI service not wired | Check ping, set API key |
| AI invents values without tool calls | System prompt not applied (AiService not configured) | Verify `@ConditionalOnExpression` resolved to true |
| Tool fires but returns 500 | Backend endpoint down (parameters, ratios, dashboard, stress-test services) | Test backend directly: `GET /ratios`, `GET /dashboard/date/...` |
| Correct tool fires but wrong date used | Date parsing in user message failed — QueryIntentExtractor | Provide explicit ISO date `YYYY-MM-DD` in the message |
| Session messages missing after turn | JPA memory store issue | Check `ai.chat_messages` table in DB |
| Title never generated | Async thread pool exhausted or GPT-4o-mini call failing | Check `ai-async-` thread pool logs |
| Rate limit not triggering | `AiRateLimitFilter` not registered or path mismatch | Verify filter applies to `/ai/chat` path |
| `429` on first request | Rate limit counter not reset between test runs | Use a different `X-User-Id` header for each test group |
