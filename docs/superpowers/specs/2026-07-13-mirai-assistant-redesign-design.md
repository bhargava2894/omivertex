# Mirai — assistant rename + premium "aurora glow" redesign

**Date:** 2026-07-13 · **Status:** Approved

## What & why

Rename the dashboard AI assistant from "OmiVertex AI" to **Mirai** and give it a
premium, powerful look. Scope is deliberately tight: restyle the existing
Dashboard card in place (no floating launcher, no new pages) and rename the
assistant everywhere including its own persona.

Look & feel: **aurora gradient + glow** — a dark, gem-toned header band with a
slow-drifting sheen, a glowing wordmark, gradient send button, and a shimmer
typing indicator.

## Naming changes

- **Frontend** (`AssistantChat.jsx`): header "Ask OmiVertex AI" → **Mirai**
  wordmark; tagline "your workforce, answered" in the band **replaces** the
  current long descriptive hint line (it is dropped for a cleaner, premium
  header); input placeholder
  "Ask Mirai…"; the busy affordance and model-message mark stay but restyled.
- **Backend** (`AssistantContextBuilder.build()`): the standing prompt's first
  sentence changes from "You are the OmiVertex AI Assistant for Softility's
  internal resource-management platform." to "You are **Mirai**, the AI
  assistant for Softility's internal resource-management platform." The rest of
  the prompt (tool guidance, rules, aggregates) is unchanged. No test asserts the
  persona string, so the suite stays green; a new test pins that the standing
  context contains "Mirai".

## Design tokens (both themes, no raw hex inline)

Add to `styles.css` under both the light `:root` and the dark theme block:

- `--mirai-band`: the header gradient (blue→violet, deepened for the dark band),
  e.g. light `linear-gradient(110deg, #1e3a8a, #4c1d95)`, dark a touch brighter.
- `--mirai-band-sheen`: a translucent white/─color overlay gradient animated
  across the band for the aurora drift.
- `--mirai-glow`: the focus/hover glow color (violet-blue, low alpha).
- `--mirai-accent`: the send-button gradient (reuse/extend `--color-primary-grad`
  toward violet).

All new colors live in these tokens; components reference `var(--mirai-*)`.
Contrast of wordmark/tagline text on `--mirai-band` must be ≥4.5:1 (verified).

## Component & style changes

**`.assistant-card`** — drop the flat `border-left` accent; add a gradient glow
ring (border or layered box-shadow using `--mirai-glow`), deepened blue-tinted
shadow, `overflow: hidden` for the band, rounded corners unchanged.

**Header band** — a new element at the card top filling width, background
`--mirai-band` with an animated `--mirai-band-sheen` overlay (background-position
drift, ~8s, ease-in-out, infinite). Contains:
- the **Mirai** wordmark (bright/gradient-clipped text on the dark band) with a
  small orbit/spark mark (reuse `Icon name="sparkles"`, restyled; no new asset
  required), and
- the tagline in muted light text.

**Suggestion chips** — pills with a subtle gradient border and a glow on hover
(`--mirai-glow`).

**Typing indicator** — replace the plain "Thinking…" text with an animated
three-dot pulse (staggered opacity/scale) using `--mirai-accent`.

**Input focus** — upgrade the existing `box-shadow` focus ring to a
`--mirai-glow` gradient ring.

**Send button** — `--mirai-accent` gradient fill, soft glow, arrow glyph
(`Icon name="arrow-right"` or similar existing icon), hover lift; keeps the
disabled state.

**Draft/confirm action card** (propose_allocation) — unchanged behavior; only
inherits the refreshed tokens if it referenced the old accent.

## Motion & accessibility

- All animations (aurora drift, sheen, typing dots, hover glow) are guarded:
  under `prefers-reduced-motion: reduce` the band shows a static gradient, the
  typing indicator becomes static text/dots, no drift. The existing global CSS
  reduced-motion kill-switch plus explicit `animation: none` overrides cover it.
- ARIA and roles unchanged (`aria-live` on the log, labelled input/button).
- Works in light and dark themes; dark-mode band uses its own token value.

## Testing & verification

- Backend: `./mvnw test` green; add `standingContext_mentionsMirai` (asserts
  `build()` contains "Mirai").
- Frontend: `cd frontend && npm run format && npm run build` (Prettier + ESLint).
- Live check against the running app: Mirai card renders with the band, wordmark,
  gradient send, and typing indicator; ask a question and confirm a reply; toggle
  dark mode; emulate reduced motion and confirm animations stop; confirm the
  assistant refers to itself as "Mirai".

## Out of scope

Floating launcher / cross-page presence, a bespoke SVG logo asset, and any
change to the assistant's tools or answers (that work shipped separately).
