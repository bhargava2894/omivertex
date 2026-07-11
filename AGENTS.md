# OmiVertex — Engineering Conventions (all AI agents & humans)

**This is the single source of truth.** `CLAUDE.md` and `GEMINI.md` just point here.
Read it before writing code. It is intentionally short — follow it exactly; when a rule
here conflicts with habit, this wins.

Stack: Spring Boot 3.5 / Java 21 · PostgreSQL (H2 in tests) · React 18 + Vite.
The **workforce graph is the product**; keep its data true and its code consistent.

---

## How to work here

0. **Feature work follows spec → plan → TDD.** Before building a feature, a short
   design spec goes to `docs/superpowers/specs/YYYY-MM-DD-<topic>-design.md` (what &
   why, approved by the user) and an implementation plan to
   `docs/superpowers/plans/` (bite-sized TDD tasks). Bug fixes can skip the spec but
   never the failing test. This applies to every agent — Claude, Gemini,
   ChatGPT/Codex — not just ones with a plugin that enforces it.
   Plan docs are **write-once scaffolding** for a single implementation run — never
   update an old plan. Specs and `docs/TECHNICAL.md` are the living documentation;
   plans stay as historical record only.
1. **TDD, always.** Write the failing test first (`src/test/java/.../api/`), watch it
   fail for the right reason, then write minimal code to pass. No production code
   without a red test first. Run `./mvnw test` — **the full suite must be green before
   every commit.** Never commit red.
2. **Consult the graph before exploring.** A graphify knowledge graph lives in
   `graphify-out/`. Before answering architecture questions or hunting through files,
   read `graphify-out/GRAPH_REPORT.md` (god nodes + structure). Open the *minimum*
   files needed.
3. **After changing code, refresh the graph:**
   `python3 -c "from graphify.watch import _rebuild_code; from pathlib import Path; _rebuild_code(Path('.'))"`
4. **Small, focused commits**, one concern each. End every commit message with:
   `Co-Authored-By: <your model> <noreply@anthropic.com>` (or your tool's equivalent).
5. **Build the frontend** with `cd frontend && npm run build` (outputs to
   `frontend/dist`; Maven copies it into the jar). Never put build output under `src/`.
6. **Update docs when behavior changes:** `docs/TECHNICAL.md` (contract) and
   `docs/TODO.md` (backlog / decisions).

## Backend rules (with examples)

- **Fixed-value fields are enums, never `String`.** Status, role, mode, level, type.
  ✅ `@Enumerated(EnumType.STRING) private Role role;` (see `Role`, `AccessStatus`,
  `WorkMode`, `Proficiency`, `PositionStatus`).
  ❌ `private String role = "VIEWER"; // ADMIN, VIEWER` — this caused real drift.
- **The web boundary speaks DTOs, never entities.** Controllers accept `*Request`
  records and return `*Response` records (see `web/dto/`). Never return a JPA entity.
- **Business rules + audit live in the service layer**, not controllers. Every mutation
  calls `AuditService.record(...)`. Constructor injection only (no field `@Autowired`).
- **No magic numbers.** Name them as constants with a comment (see the bench/roll-off/
  cert-expiry horizons in `DashboardService`).
- **One implementation per cross-cutting rule.** Don't re-derive a convention inline —
  put it in one place and call it (see `EmailNaming` for the name→email rule).
- **Errors** go through `GlobalExceptionHandler` via typed exceptions
  (`NotFoundException` 404, `ConflictException` 409, `BadRequestException` 400,
  `UnauthorizedException` 401). Don't hand-roll error responses.
- **Protective invariants are non-negotiable:** capacity guard (a person ≤ 100%),
  uniqueness, protective deletes, tenant/role checks. If you touch them, test them.

## Frontend rules (with examples)

- **Colors come from CSS tokens, never raw hex.** ✅ `var(--color-primary)`,
  `var(--chart-2)`. ❌ `#2563eb` inline. ❌ `var(--color-success, #10b981)` — a hex
  *fallback* inside `var()` is still raw hex; if the token doesn't exist in
  `styles.css`, find the right existing token (greens are `--color-accent`), don't
  invent one. Tokens make dark mode work automatically (`styles.css` defines both
  themes).
- **One shared module per cross-cutting concern.** ✅ import from `proficiency.js`
  for skill labels/tones/colors. ❌ re-declaring `PROF_COLORS`/`PROF_LABELS` per page —
  two copies already drifted and disagreed. If two screens need the same map, extract it.
- **Reuse the shared components:** `DataTable`, `Modal`, `Field`, `Badge`, `Icon`.
  Don't rebuild a table or modal inline.
- **Respect roles:** pages receive `canEdit`; hide mutation controls when false. The
  server enforces it too — but the UI must not offer actions the user can't take.
- **Prefer server-driven lists** where volume warrants (see `Associates` +
  `DataTable serverPagination`), not shipping whole tables to the browser.

## Cross-cutting principles

- **One source of truth per concept.** If you find yourself copying a value, rule, or
  mapping, stop and extract it. Duplication is how drift starts.
- **Match the surrounding code.** Same naming, same structure, same test style as the
  files next to yours. Consistency beats personal preference.
- **When you must deviate from a rule, say so** in the PR/commit and, if it's a lasting
  choice, record it under "Resolved decisions" in `docs/TODO.md` (see the legacy
  `primarySkill` note there for the format).

## Automated enforcement (the build checks this for you)

Guardrails run on the build and fail it regardless of which agent wrote the code, so
you don't have to remember the rules.

**Backend** — two run on every `./mvnw test`:

- **Spotless** (code hygiene): unused imports, trailing whitespace, missing final
  newline. If the build complains, run **`./mvnw spotless:apply`** to auto-fix, then commit.
- **ArchUnit** (`src/test/java/.../ArchitectureTest.java`): encodes the structural rules
  above — domain entities stay pure, nothing depends on controllers, `status`/`role`
  fields are enums not String, controllers return DTOs not entities, naming/placement.
  When you add a mechanically-checkable convention, add a rule there too.

**Frontend** — `npm run build` runs `prebuild → check` first, so it fails on violations:

- **Prettier** (formatting): run **`npm run format`** to auto-fix, `npm run format:check`
  to verify. Config in `.prettierrc.json`.
- **ESLint** (`eslint.config.js`): real bugs are errors and block the build (undefined
  vars, unused vars/imports, rules-of-hooks, JSX correctness). The React-Compiler-era
  advisory rules (`exhaustive-deps`, `set-state-in-effect`, `immutability`, `use-memo`)
  are warnings — they surface but don't block, because they flag intentional patterns
  (e.g. the generic `useLoad(loader, deps)` hook). Run **`npm run lint`**.

## Definition of Done — run this before you stop, every agent, every time

Do not end your turn with the working tree dirty and these unchecked. "The code is
written" is not done; **verified + documented + committed** is done. If you must stop
early (limit, handoff), say exactly which of these you skipped so the next agent
(whoever it is) can pick up.

1. `./mvnw test` — full suite green (Spotless + ArchUnit included). If Spotless
   complains: `./mvnw spotless:apply`, rerun.
2. `cd frontend && npm run format && npm run build` — if you touched any frontend
   file. The build runs Prettier + ESLint; a skipped build is how unformatted or
   broken JSX gets handed to the next agent.
3. **Docs updated** — `docs/TECHNICAL.md` for any behavior/contract change
   (entities, endpoints, business rules, response shapes — all of them, not just the
   tables) and `docs/TODO.md` "Resolved decisions" for any lasting choice.
4. **Graph refreshed** —
   `$(cat graphify-out/.graphify_python) -c "from graphify.watch import _rebuild_code; from pathlib import Path; _rebuild_code(Path('.'))"`
5. **Committed** — small, focused commits, message ends with your co-author line
   (`Co-Authored-By: <your model> <noreply@...>`). Uncommitted work is invisible
   work: it can't be reviewed, reverted, or attributed.

## Known deliberate exceptions (don't "fix" these blindly)

- `primarySkill`/`secondarySkill` on `Associate` are informal free-text headline fields,
  **superseded** by the structured `AssociateSkill` graph. Kept for import + a matching
  fallback. Do not build new features on them.
- Clients/Projects/Allocations lists are still client-paged (small, derived-field
  filters) — server pagination there is a tracked P3 item, not an oversight.
