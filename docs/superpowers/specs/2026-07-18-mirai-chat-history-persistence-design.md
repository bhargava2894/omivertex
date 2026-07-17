# Mirai — chat history survives a refresh

**Date:** 2026-07-18 · **Status:** Approved

## What & why

A page reload (common now that the intro animation invites them) wipes the
Mirai conversation. The chat card's `messages` state is persisted to
**`sessionStorage`** — per-tab, cleared when the tab closes, never shared
across tabs — so a refresh restores the conversation exactly, including
pending draft cards and their confirmed state. Deliberately NOT
`localStorage` and NOT server-side: roster data in replies gets no durable
home outside the entity tables (same posture as the log-not-DB decision).
Per-reply feedback was considered and **descoped by the user**.

## Design

- `AssistantChat.jsx`: `messages` initializes from
  `sessionStorage['mirai-chat']` (lenient JSON parse — malformed/absent →
  `[]`); a `useEffect` writes back on every change, capped at the **last 40
  messages** (quota bound; storage failures are swallowed). The key lives in
  one exported constant, `MIRAI_CHAT_KEY`.
- **Clear chat**: a small icon button (existing `trash` icon, band-styled
  like the expand button) empties the state and removes the key, bringing
  the suggestion chips back. Hidden while a turn is in flight.
- **Logout** (`App.jsx`) removes the key explicitly — a shared machine must
  not show the next login the previous user's chat within the same tab.

## Testing & verification

Frontend-only, no JS test infra: `npm run format && npm run build`, then a
live check — ask a question, reload, conversation intact (draft cards too);
clear chat resets to chips; logout + login shows an empty chat.
`docs/TECHNICAL.md` gets one sentence on the persistence posture;
`docs/TODO.md` records the sessionStorage-not-server decision. Graph
refreshed.

## Out of scope

Feedback buttons (user descope), cross-tab or server-side history, history
size settings, token streaming.
