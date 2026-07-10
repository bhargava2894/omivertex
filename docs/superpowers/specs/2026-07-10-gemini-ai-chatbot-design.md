# Design Specification — AI Assistant on the Dashboard (Gemini-powered)

*Author: Antigravity AI (draft) · revised per Claude review + user decisions 2026-07-10*
*Date: 2026-07-10*
*Status: Approved with revisions (see §6)*

---

## 1. What & Why (Business Objective)

OmiVertex houses the workforce graph (associates, skills, allocations, and pipeline demand). While the dashboard provides structured aggregations, managers frequently need complex, cross-cutting answers that are difficult to query via fixed tables, such as:
- *\"Find me an offshore backend developer with at least 50% capacity who has worked with Java in the past.\"*
- *\"Summarize our current skill gaps and suggest which certifications we should sponsor next month based on active demand.\"*
- *\"Are there any upcoming project roll-offs in the next 30 days that put project X at risk?\"*

### Objective
Integrate the **Google Gemini API** directly into the Spring Boot backend to power an interactive, conversational **AI Assistant** on the managers' dashboard. This allows natural language queries over the live workforce graph without exposing data directly to public search indexes or configuring complex external search infrastructures (like vector search or RAG).

---

## 2. Core Architecture

```
React UI (GeminiChat.jsx)
   │  POST /api/v1/gemini/chat { message, history }
   ▼
Spring Boot 3.5 Controller (GeminiController.java) ── [Role Check: ADMIN, VIEWER]
   │  
   ▼
GeminiService.java
   ├─ Compiles active database context (Roster, Allocations, Demand)
   ├─ Loads system role & prompt rules
   └─ REST Client POST to Gemini API
         │  https://generativelanguage.googleapis.com/...
         ▼
     Google Gemini API (1.5-flash / 2.5-flash)
```

### Context Synthesis
Instead of setting up vector databases, the backend compiles a structured, dense Markdown text summary of the database's active entities (Roster, Skills, Allocations, and Open Positions) on-the-fly and attaches it to the model's system prompt instructions. Since the total size of these entities is small (< 1,000 records), it fits comfortably within Gemini's extensive context window (up to 1M tokens) while maintaining high speed and accuracy.

---

## 3. API Contracts

### Endpoints
* **Path**: `/api/v1/gemini/chat`
* **Method**: `POST`
* **Security**: Roles `ADMIN`, `VIEWER` only. Rejected for `ASSOCIATE`.

### Request Payload (`GeminiChatRequest`)
```json
{
  "message": "Which offshore developers are available to match to Project ACM-100?",
  "history": [
    {
      "role": "user",
      "content": "Hello!"
    },
    {
      "role": "model",
      "content": "Hello! I am your OmiVertex AI Assistant. Ask me anything."
    }
  ]
}
```

### Response Payload (`GeminiChatResponse`)
```json
{
  "reply": "Based on our current bench, Priya Sharma is offshore and has 100% availability. She holds advanced Java skills which match ACM-100."
}
```

---

## 4. Security & Compliance Gating

1. **Role Gating**:
   - The `/api/v1/gemini/**` namespace is blocked for the `ASSOCIATE` role. Only delivery managers and administrators (`ADMIN`/`VIEWER`) are authorized to ask questions over aggregate resource allocations.
2. **Fail-Closed Configuration**:
   - The Gemini API Key is loaded via properties: `omivertex.gemini.api-key`.
   - If the key is not set or empty, the backend returns a `400 Bad Request` with a clear explanation rather than crashing or returning blank replies.
3. **Outbound Firewall Rules**:
   - Requires the deployment server to allow HTTPS requests to `generativelanguage.googleapis.com`. No inbound ports need opening.

---

## 5. UI/UX Design

- **Navigation**: ~~sidebar link~~ **Revised (user decision): a full-width "Ask OmiVertex AI"
  card on the Dashboard itself** — part of the morning briefing, no separate page.
- **Visuals**: A clean, modern chat view inside the dashboard card:
  - Scrollable chat log (component state; history resets on reload — stateless server).
  - Sparkle icon for AI messages, user/avatar icon for user messages.
  - Lightweight formatting of replies (paragraphs, bullets, bold) rendered as React
    elements — no `dangerouslySetInnerHTML`, no new markdown dependency.
  - Pre-defined **Starter Suggestion Chips** (e.g. "Who is on the bench?", "Which
    open positions have no bench match?", "Who rolls off in the next 30 days?").

---

## 6. Review revisions (2026-07-10, approved)

1. **Vendor-neutral API.** Endpoint is `/api/v1/assistant/chat`; classes are
   `AssistantController` / `AssistantService`. The vendor lives behind an injectable
   `GeminiClient` interface (same config-gated pattern as `GoogleTokenVerifier`), so
   tests mock the client — the suite never calls Google — and the vendor can change
   without breaking the API contract.
2. **Security rule fix.** The blanket rule gives VIEWER only GETs, so the chat POST
   needs an explicit matcher: `/api/v1/assistant/**` → `hasAnyRole("ADMIN","VIEWER")`,
   placed before the blanket rules. ASSOCIATE stays excluded.
3. **Data sent to Google (user decision: FULL detail).** The per-question context
   includes the complete active workforce picture — associates with emails, skills,
   proficiencies, bench days, allocations, clients/projects, open positions with
   requirements, exit data, and dashboard KPIs. Resume file contents are NOT sent
   (binary, large, low value). Recorded as a deliberate decision in docs/TODO.md.
4. **Config.** `omivertex.assistant.gemini.api-key` (env
   `OMIVERTEX_ASSISTANT_GEMINI_API_KEY`) and `omivertex.assistant.gemini.model`
   (default `gemini-2.5-flash`). Missing key → 400 "AI assistant is not configured"
   (fail closed, matches the existing error contract). Upstream/API failures → 400
   with a human-readable message; never a blank reply or a 500.
5. **Limits.** `message` required, ≤ 2,000 chars → else 400; history capped at the
   last 20 turns server-side; 30s HTTP timeout to the Gemini API.
6. **Placement (user decision).** Dashboard card, not a sidebar page.
