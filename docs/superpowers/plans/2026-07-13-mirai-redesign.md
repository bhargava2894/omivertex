# Mirai Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename the dashboard AI assistant to "Mirai" (UI + persona) and restyle its card with a premium "aurora gradient + glow" look.

**Architecture:** One backend string change (the assistant's system prompt) plus a frontend restyle of the existing `AssistantChat` card: a new full-bleed gradient header band with the Mirai wordmark, a gradient send button, a shimmer typing indicator, and gradient focus/hover glows. All new colors are CSS tokens defined for both light and dark themes; motion is reduced-motion-guarded.

**Tech Stack:** Spring Boot (one prompt string) · React 18 + Vite + framer-motion (already used). Backend gate: `./mvnw test` (TDD). Frontend gate: `npm run build` (Prettier + ESLint); no frontend unit-test harness, so frontend tasks are verified by the build plus a live check.

**Spec:** `docs/superpowers/specs/2026-07-13-mirai-assistant-redesign-design.md`

Every commit ends with:

```
Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
```

---

### Task 1: Backend — assistant persona becomes "Mirai" (TDD)

**Files:**
- Test: `src/test/java/com/softility/omivertex/api/AssistantContextBuilderTest.java`
- Modify: `src/main/java/com/softility/omivertex/service/AssistantContextBuilder.java:79`

- [ ] **Step 1: Write the failing test**

Add this test method inside `AssistantContextBuilderTest` (next to the other `standingContext_*` tests):

```java
    @Test
    void standingContext_mentionsMirai() {
        seedWorkforce();
        assertThat(builder.build()).contains("Mirai");
    }
```

- [ ] **Step 2: Run it — expect RED**

Run: `./mvnw test -Dtest=AssistantContextBuilderTest#standingContext_mentionsMirai`
Expected: FAIL — the standing context says "OmiVertex AI Assistant", not "Mirai".

- [ ] **Step 3: Rename the persona in the prompt**

In `AssistantContextBuilder.java`, the `build()` method currently starts the returned string with (line 79):

```java
        return "You are the OmiVertex AI Assistant for Softility's internal resource-management "
```

Replace that one line with:

```java
        return "You are Mirai, the AI assistant for Softility's internal resource-management "
```

Leave the rest of the prompt (the following string-concatenation lines: tools, rules, aggregates) exactly as-is.

- [ ] **Step 4: Run it — expect GREEN**

Run: `./mvnw test -Dtest=AssistantContextBuilderTest`
Expected: PASS (all tests in the class, including the new one).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/softility/omivertex/service/AssistantContextBuilder.java \
  src/test/java/com/softility/omivertex/api/AssistantContextBuilderTest.java
git commit -m "feat: assistant persona renamed to Mirai"
```

---

### Task 2: Frontend — Mirai CSS tokens (both themes) + a send icon

**Files:**
- Modify: `frontend/src/styles.css` (the `:root {` block at line 1, and the `[data-theme='dark'] {` block at line 44)
- Modify: `frontend/src/components/Icon.jsx`

- [ ] **Step 1: Add Mirai tokens to the light theme**

In `frontend/src/styles.css`, inside the `:root { … }` block, add these lines just after the existing `--color-primary-grad: …;` line (near the top, ~line 4):

```css
  --mirai-band: linear-gradient(110deg, #1e3a8a 0%, #4c1d95 55%, #6d28d9 100%);
  --mirai-band-sheen: linear-gradient(110deg, transparent 30%, rgba(255, 255, 255, 0.18) 50%, transparent 70%);
  --mirai-band-fg: rgba(255, 255, 255, 0.96);
  --mirai-band-muted: rgba(226, 232, 240, 0.74);
  --mirai-glow: rgba(124, 58, 237, 0.32);
  --mirai-accent: linear-gradient(120deg, #3b82f6, #7c3aed);
```

- [ ] **Step 2: Add Mirai tokens to the dark theme**

In the same file, inside the `[data-theme='dark'] { … }` block (starts line 44), add just after its `--color-primary-grad: …;` line:

```css
  --mirai-band: linear-gradient(110deg, #1e40af 0%, #5b21b6 55%, #7c3aed 100%);
  --mirai-band-sheen: linear-gradient(110deg, transparent 30%, rgba(255, 255, 255, 0.14) 50%, transparent 70%);
  --mirai-band-fg: rgba(255, 255, 255, 0.96);
  --mirai-band-muted: rgba(226, 232, 240, 0.7);
  --mirai-glow: rgba(139, 92, 246, 0.4);
  --mirai-accent: linear-gradient(120deg, #4f8ef7, #8b5cf6);
```

(Wordmark/tagline are white / light-slate on a deep navy-violet band → well above 4.5:1 in both themes.)

- [ ] **Step 3: Add a `send` icon**

In `frontend/src/components/Icon.jsx`, add one entry to the icon map (put it next to `sparkles`). The map entries look like `name: <path d="…" />,`. Add:

```jsx
  send: (
    <>
      <path d="M22 2 11 13" />
      <path d="M22 2 15 22 11 13 2 9Z" />
    </>
  ),
```

- [ ] **Step 4: Build**

Run: `cd frontend && npm run format && npm run build`
Expected: success, no ESLint errors. (Tokens/icon aren't referenced yet — that's fine.)

- [ ] **Step 5: Commit**

```bash
git add frontend/src/styles.css frontend/src/components/Icon.jsx
git commit -m "feat: Mirai color tokens (light+dark) and send icon"
```

---

### Task 3: Frontend — restyle the AssistantChat card as Mirai

**Files:**
- Modify: `frontend/src/components/AssistantChat.jsx` (render block, ~lines 124–248)
- Modify: `frontend/src/styles.css` (replace the `/* AI Assistant Chat */` block, lines 1032–1104)

- [ ] **Step 1: Replace the card header with the Mirai band + open a body wrapper**

In `AssistantChat.jsx`, replace this exact block (currently lines 124–132):

```jsx
  return (
    <div className="card panel assistant-card">
      <h2>
        <Icon name="sparkles" size={15} /> Ask OmiVertex AI
      </h2>
      <p className="stat-hint" style={{ marginTop: 0 }}>
        Natural-language questions over the live workforce data — bench, projects, demand,
        roll-offs.
      </p>
```

with:

```jsx
  return (
    <div className="card panel mirai-card">
      <div className="mirai-band">
        <div className="mirai-brand">
          <span className="mirai-mark" aria-hidden="true">
            <Icon name="sparkles" size={18} />
          </span>
          <span className="mirai-wordmark">Mirai</span>
        </div>
        <p className="mirai-tagline">your workforce, answered</p>
      </div>
      <div className="mirai-body">
```

- [ ] **Step 2: Close the body wrapper before the card closes**

Still in `AssistantChat.jsx`, the render currently ends with the `</form>` then the card's `</div>` (lines 247–248):

```jsx
      </form>
    </div>
  );
```

Replace it with (add one closing `</div>` for `.mirai-body`):

```jsx
      </form>
      </div>
    </div>
  );
```

- [ ] **Step 3: Update the input placeholder**

In the same file, change the input placeholder (currently line 236):

```jsx
          placeholder="e.g. How many people are on the bench, and what projects are running?"
```

to:

```jsx
          placeholder="Ask Mirai…"
```

- [ ] **Step 4: Make the send button an icon**

Change the submit button's label (currently line 245) from:

```jsx
          {busy ? '…' : 'Ask'}
```

to:

```jsx
          {busy ? '…' : <Icon name="send" size={16} />}
```

- [ ] **Step 5: Replace the "Thinking…" text with a typing indicator**

Change the busy line inside the message log (currently line 222):

```jsx
          {busy && <div className="stat-hint">Thinking…</div>}
```

to:

```jsx
          {busy && (
            <div className="mirai-typing" aria-label="Mirai is thinking">
              <span />
              <span />
              <span />
            </div>
          )}
```

- [ ] **Step 6: Replace the assistant CSS block**

In `frontend/src/styles.css`, replace the entire block from the comment `/* ---------- AI Assistant Chat ---------- */` (line 1032) through the closing `}` of the reduced-motion rule at line 1104 with:

```css
/* ---------- Mirai assistant ---------- */
.mirai-card {
  margin-bottom: 24px;
  padding: 0;
  overflow: hidden;
  position: relative;
  box-shadow: 0 10px 34px var(--mirai-glow), var(--shadow-sm);
  transition: box-shadow 0.25s var(--ease);
}
.mirai-card:hover {
  box-shadow: 0 16px 44px var(--mirai-glow), var(--shadow-md);
}

.mirai-band {
  position: relative;
  padding: 18px 20px 16px;
  background: var(--mirai-band);
  overflow: hidden;
}
.mirai-band::after {
  content: '';
  position: absolute;
  inset: 0;
  background: var(--mirai-band-sheen);
  background-size: 250% 100%;
  animation: mirai-aurora 8s ease-in-out infinite;
  pointer-events: none;
}
.mirai-brand {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
}
.mirai-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 9px;
  color: #fff;
  background: rgba(255, 255, 255, 0.14);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.25);
  animation: mirai-mark-pulse 3s ease-in-out infinite;
}
.mirai-wordmark {
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.01em;
  color: var(--mirai-band-fg);
}
.mirai-tagline {
  position: relative;
  margin: 6px 0 0;
  font-size: 12.5px;
  color: var(--mirai-band-muted);
}
.mirai-body {
  padding: 16px 20px 18px;
}

@keyframes mirai-aurora {
  0% { background-position: 0% 0; }
  50% { background-position: 100% 0; }
  100% { background-position: 0% 0; }
}
@keyframes mirai-mark-pulse {
  0%, 100% { box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.25); }
  50% { box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.4), 0 0 12px 2px rgba(255, 255, 255, 0.22); }
}

.mirai-typing {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  padding: 4px 2px;
}
.mirai-typing span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--mirai-accent);
  animation: mirai-dot 1.2s ease-in-out infinite;
}
.mirai-typing span:nth-child(2) { animation-delay: 0.15s; }
.mirai-typing span:nth-child(3) { animation-delay: 0.3s; }
@keyframes mirai-dot {
  0%, 100% { opacity: 0.3; transform: translateY(0); }
  50% { opacity: 1; transform: translateY(-3px); }
}

.mirai-body .btn-ghost.btn-sm {
  border: 1px solid var(--color-border);
  transition: border-color 0.18s var(--ease), box-shadow 0.18s var(--ease);
}
.mirai-body .btn-ghost.btn-sm:hover {
  border-color: transparent;
  box-shadow: 0 0 0 1px var(--mirai-glow), 0 2px 10px var(--mirai-glow);
}

.assistant-form {
  display: flex;
  gap: 8px;
  margin-top: 14px;
}
.assistant-input {
  flex: 1;
  padding: 12px 16px;
  font-size: 14.5px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  color: var(--color-foreground);
  box-shadow: inset 0 1px 2px rgba(0, 0, 0, 0.02), var(--shadow-sm);
  transition: border-color 0.22s var(--ease), box-shadow 0.22s var(--ease), background 0.22s var(--ease);
}
.assistant-input:hover:not(:disabled) {
  border-color: var(--color-primary-soft);
  background: var(--color-surface-hover);
}
.assistant-input:focus:not(:disabled) {
  outline: none;
  border-color: transparent;
  background: var(--color-surface);
  box-shadow: 0 0 0 3px var(--mirai-glow), inset 0 1px 2px rgba(0, 0, 0, 0.02);
}
.assistant-input:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.assistant-btn {
  display: inline-flex !important;
  align-items: center !important;
  justify-content: center !important;
  border-radius: var(--radius-md) !important;
  min-height: 46px !important;
  min-width: 54px !important;
  padding: 0 18px !important;
  background: var(--mirai-accent) !important;
  border: none !important;
  color: #fff !important;
  box-shadow: 0 4px 14px var(--mirai-glow) !important;
  transition: transform 0.15s var(--ease), box-shadow 0.2s var(--ease) !important;
}
.assistant-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 6px 20px var(--mirai-glow) !important;
}

@media (prefers-reduced-motion: reduce) {
  .mirai-band::after,
  .mirai-mark,
  .mirai-typing span {
    animation: none !important;
  }
}
```

- [ ] **Step 7: Format and build**

Run: `cd frontend && npm run format && npm run build`
Expected: success, no ESLint errors. If ESLint flags an unused `Icon` import — it's still used (mark + send + message icon), so it should not.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/components/AssistantChat.jsx frontend/src/styles.css
git commit -m "feat: Mirai aurora-glow assistant card (band, gradient send, typing indicator)"
```

---

### Task 4: Definition of Done — verify, refresh graph, live check

**Files:** none (verification only; `graphify-out/` is not tracked).

- [ ] **Step 1: Full backend suite**

Run: `./mvnw test` (repo root)
Expected: BUILD SUCCESS, all green (includes the new `standingContext_mentionsMirai`).

- [ ] **Step 2: Frontend build gate**

Run: `cd frontend && npm run format && npm run build`
Expected: success (Prettier + ESLint + Vite).

- [ ] **Step 3: Refresh the knowledge graph**

```bash
$(cat graphify-out/.graphify_python) -c "from graphify.watch import _rebuild_code; from pathlib import Path; _rebuild_code(Path('.'))"
```

- [ ] **Step 4: Live check (dev servers on :8080 / :5173, login admin/Admin@123)**

On `#/` (Dashboard):
1. The Mirai card shows the gradient band, "Mirai" wordmark + glowing mark, and the "your workforce, answered" tagline.
2. The send button is a gradient pill with the send icon; focusing the input shows a violet glow ring; suggestion chips glow on hover.
3. Ask a question → a reply renders; while waiting, the three-dot typing indicator animates.
4. Toggle dark mode (top bar) → band and glows still read well, text legible.
5. Emulate reduced motion (`page.emulateMedia({ reducedMotion: 'reduce' })` or OS setting) → aurora drift, mark pulse, and typing dots stop; layout intact.
6. If the local-profile Gemini key is available, confirm the assistant refers to itself as "Mirai" when asked "who are you?".

If no environment is available, note the skipped live check in the final report (AGENTS.md Definition of Done handoff).
