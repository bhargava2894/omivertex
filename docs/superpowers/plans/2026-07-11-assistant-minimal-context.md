# Assistant Minimal Context + Read Tools Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The assistant's standing context shrinks to aggregate counts only (no names/emails); the model fetches relevant slices on demand through four new read tools, so per-question data sent to Gemini is bounded regardless of roster size.

**Architecture:** `AssistantContextBuilder` becomes the single data-formatter: `build()` returns instructions+counts; new methods `searchAssociates`, `associateDetail`, `rolloffs`, `openPositions` format tool results (≤ 25 rows each). `AssistantService.executeReadTool` dispatches five read tools; `GeminiHttpClient` declares them and allows 3 tool rounds. Write actions and frontend unchanged.

**Tech Stack:** Existing Gemini function-calling loop (`ToolExecutor`), stubbed-Gemini MockMvc tests.

**Branch:** `feature/assistant-minimal-context`. Spec: `docs/superpowers/specs/2026-07-11-assistant-minimal-context-design.md`.

---

### Task 1: Failing tests — privacy pin + tool behavior

**Files:**
- Modify: `src/test/java/com/softility/omivertex/api/AssistantContextBuilderTest.java` (full rewrite)
- Modify: `src/test/java/com/softility/omivertex/api/AssistantApiTest.java` (context assertion flip + 4 tool tests)

- [ ] **Step 1: Rewrite `AssistantContextBuilderTest`**

```java
package com.softility.omivertex.api;

import com.softility.omivertex.domain.ExitReason;
import com.softility.omivertex.domain.OpenPosition;
import com.softility.omivertex.domain.PositionSkill;
import com.softility.omivertex.domain.Proficiency;
import com.softility.omivertex.domain.WorkMode;
import com.softility.omivertex.service.AssistantContextBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantContextBuilderTest extends ApiTestBase {

    @Autowired AssistantContextBuilder builder;

    private void seedWorkforce() {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var java = skill("Backend", "Java");
        var staffed = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        rateSkill(staffed, java, Proficiency.ADVANCE);
        allocation(staffed, proj, true);
        associate("Rahul Verma", "rahul@softility.com", WorkMode.ONSHORE); // bench
        var position = new OpenPosition();
        position.setTitle("Java Dev");
        position.setProject(proj);
        openPositionRepository.save(position);
        var req = new PositionSkill();
        req.setPosition(position);
        req.setSkill(java);
        req.setMinProficiency(Proficiency.INTERMEDIATE);
        req.setRequired(true);
        positionSkillRepository.save(req);
    }

    @Test
    void standingContext_containsOnlyAggregates_neverPersonalData() {
        seedWorkforce();
        String context = builder.build();

        // privacy pin: no roster data in the always-sent context
        assertThat(context).doesNotContain("Priya Sharma");
        assertThat(context).doesNotContain("priya@softility.com");
        assertThat(context).doesNotContain("Rahul Verma");
        // aggregates present
        assertThat(context).contains("Active associates: 2");
        assertThat(context).contains("Bench count: 1");
        assertThat(context).contains("Open positions: 1");
        assertThat(context).contains("Clients: 1");
        assertThat(context).contains("Projects: 1");
    }

    @Test
    void searchAssociates_filtersBySkillAndBench_capsRows() {
        seedWorkforce();
        // bench + Java at INTERMEDIATE+: nobody (Priya is allocated, Rahul unrated)
        assertThat(builder.searchAssociates(null, "Java", Proficiency.INTERMEDIATE, true))
                .contains("No matching associates");
        // Java at INTERMEDIATE+ regardless of bench: Priya
        String result = builder.searchAssociates(null, "Java", Proficiency.INTERMEDIATE, false);
        assertThat(result).contains("Priya Sharma").contains("Storefront Revamp");
        assertThat(result).doesNotContain("Rahul Verma");
    }

    @Test
    void associateDetail_showsSkillsAllocationsAndUpcomingExit() {
        seedWorkforce();
        var leaver = associateRepository.findAll().stream()
                .filter(a -> a.getName().equals("Priya Sharma")).findFirst().orElseThrow();
        leaver.setExitReason(ExitReason.RESIGNED);
        leaver.setLastWorkingDay(LocalDate.now().plusDays(10));
        associateRepository.save(leaver);

        String detail = builder.associateDetail(leaver);
        assertThat(detail).contains("Java (ADVANCE)");
        assertThat(detail).contains("Storefront Revamp @Acme Corp");
        assertThat(detail).contains("leaving on " + LocalDate.now().plusDays(10));
    }

    @Test
    void rolloffs_listsAllocationsEndingInWindow() {
        seedWorkforce();
        var alloc = allocationRepository.findAll().get(0);
        alloc.setEndDate(LocalDate.now().plusDays(10));
        allocationRepository.save(alloc);

        assertThat(builder.rolloffs(30)).contains("Priya Sharma").contains("Storefront Revamp");
        assertThat(builder.rolloffs(5)).contains("No allocations ending");
    }

    @Test
    void openPositions_listsTitleProjectAndMustHaves() {
        seedWorkforce();
        String result = builder.openPositions();
        assertThat(result).contains("Java Dev");
        assertThat(result).contains("must-have: Java (min INTERMEDIATE)");
    }
}
```

- [ ] **Step 2: Flip the context assertion in `AssistantApiTest.chat_answersWithWorkforceContext`**

Replace `assertThat(context.getValue()).contains("Priya Sharma");` with:

```java
// minimal-context design: the standing context carries aggregates, never roster rows
assertThat(context.getValue()).doesNotContain("Priya Sharma");
assertThat(context.getValue()).contains("Active associates: 1");
```

- [ ] **Step 3: Add 2 tool-dispatch API tests to `AssistantApiTest`** (the executor pattern already covers `get_position_matches`; these pin the new dispatch wiring end-to-end — the remaining tools are covered by the builder tests above)

```java
@Test
void chat_readTool_searchAssociates() throws Exception {
    var java = skill("Backend", "Java");
    var priya = associate("Priya Sharma", "priya@softility.com", WorkMode.ONSHORE);
    rateSkill(priya, java, com.softility.omivertex.domain.Proficiency.ADVANCE);
    when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any()))
            .thenAnswer(inv -> {
                GeminiClient.ToolExecutor ex = inv.getArgument(3);
                String result = ex.execute("search_associates",
                        Map.of("skill", "Java", "benchOnly", true));
                return new GeminiClient.AssistantReply("Found: " + result, null);
            });

    asyncPerform(post("/api/v1/assistant/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"message":"who on the bench knows java?","history":[]}"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reply", containsString("Priya Sharma")));
}

@Test
void chat_readTool_associateDetail_ambiguousNameAsksBack() throws Exception {
    associate("Priya Sharma", "priya@softility.com", WorkMode.ONSHORE);
    associate("Priya Verma", "priya.v@softility.com", WorkMode.ONSHORE);
    when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any()))
            .thenAnswer(inv -> {
                GeminiClient.ToolExecutor ex = inv.getArgument(3);
                return new GeminiClient.AssistantReply(
                        ex.execute("get_associate_detail", Map.of("name", "Priya")), null);
            });

    asyncPerform(post("/api/v1/assistant/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"message":"tell me about priya","history":[]}"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reply", containsString("more than one match")));
}
```

- [ ] **Step 4: Verify red**

Run: `./mvnw test -Dtest='AssistantContextBuilderTest,AssistantApiTest'`
Expected: COMPILE FAILURE (`searchAssociates`, `associateDetail`, `rolloffs`, `openPositions` don't exist) — red.

---

### Task 2: Implement builder methods, dispatch, declarations

**Files:**
- Modify: `src/main/java/com/softility/omivertex/service/AssistantContextBuilder.java` (rewrite `build()`, add 4 methods + row-cap constant; add `ClientRepository`/`ProjectRepository` deps)
- Modify: `src/main/java/com/softility/omivertex/service/AssistantService.java` (dispatch)
- Modify: `src/main/java/com/softility/omivertex/service/GeminiHttpClient.java` (`READ_TOOLS` set, 4 declarations, `MAX_TOOL_ROUNDS` 2 → 3)

- [ ] **Step 1: `AssistantContextBuilder`** — key shapes (constants and method signatures must match exactly):

```java
/** Max rows any tool result returns — a tool reply can never blow up the prompt. */
static final int MAX_TOOL_ROWS = 25;

public String build()                 // instructions + date + counts ONLY
public String searchAssociates(String name, String skillName, Proficiency minProficiency, boolean benchOnly)
public String associateDetail(Associate associate)
public String rolloffs(int withinDays)
public String openPositions()
```

`build()` instructions text: answer concisely from tool data; "Use your lookup tools (search_associates, get_associate_detail, list_rolloffs, list_open_positions, get_position_matches) to fetch specifics before answering. If the tools cannot answer, say so — never invent people, projects, or numbers." Counts block:

```
## Key numbers (today: <date>)
Active associates: N · Bench count: N · Open positions: N · Clients: N · Projects: N
```

`searchAssociates`: ACTIVE associates; optional name contains (case-insensitive); optional skill name contains with proficiency ≥ min (min defaults NOVICE when null); `benchOnly` = no current allocation. Row format: `- <name> · <designation|no designation> · <workMode> · BENCH for N days|allocated: Proj @Client (…)` + ` · matched: Skill (PROF)` when a skill filter was used + ` · leaving on <date>` when set. Empty → `"No matching associates."`; > cap → append `"…and N more — refine the search."`.

`associateDetail`: the old per-associate line verbatim (skills list, allocations with %/billable/end, bench days, leaving-on) — reuse the formatting that `build()` used to do per associate.

`rolloffs(days)`: current allocations of ACTIVE associates with `endDate != null && endDate <= today+days`, sorted by end date: `- <name> — <project> @<client>, ends <date> (in N days)`. Empty → `"No allocations ending within N days."`.

`openPositions()`: the old "## Open positions" section body, unchanged formatting. Empty → `"No open positions."`.

- [ ] **Step 2: `AssistantService.executeReadTool`** — replace the single-tool check with a switch:

```java
private String executeReadTool(String name, Map<String, Object> args) {
    return switch (name) {
        case "get_position_matches" -> positionMatches(args);          // existing logic, extracted
        case "search_associates" -> contextBuilder.searchAssociates(
                str(args, "name"), str(args, "skill"),
                proficiencyOrNull(str(args, "minProficiency")),
                boolOrDefault(args.get("benchOnly"), false));
        case "get_associate_detail" -> {
            Resolved<Associate> associate = resolveAssociate(str(args, "name"));
            yield associate.reply() != null ? associate.reply()
                    : contextBuilder.associateDetail(associate.value());
        }
        case "list_rolloffs" -> contextBuilder.rolloffs(intOrDefault(args.get("withinDays"), 30));
        case "list_open_positions" -> contextBuilder.openPositions();
        default -> "Unknown tool: " + name;
    };
}

private static Proficiency proficiencyOrNull(String value) {
    if (value == null || value.isBlank()) return null;
    try {
        return Proficiency.valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
        return null;
    }
}
```

- [ ] **Step 3: `GeminiHttpClient`** — `MAX_TOOL_ROUNDS = 3`; replace `READ_TOOL_MATCHES.equals(name)` routing with a set:

```java
/** Tools executed server-side and fed back to the model (write tools return as drafts). */
static final java.util.Set<String> READ_TOOLS = java.util.Set.of(
        "get_position_matches", "search_associates", "get_associate_detail",
        "list_rolloffs", "list_open_positions");
```

and four declarations appended to `FUNCTION_DECLARATIONS` (all parameters typed string/boolean/integer, only `get_associate_detail.name` required):
`search_associates(name?, skill?, minProficiency? [NOVICE..MASTERY], benchOnly?)`,
`get_associate_detail(name*)`, `list_rolloffs(withinDays?)`, `list_open_positions()`.

- [ ] **Step 4: Green + full suite + commit**

Run: `./mvnw test` → BUILD SUCCESS.

```bash
git add -A src/main src/test
git commit -m "feat: assistant sends aggregates only — roster data fetched per-question via read tools

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 3: Docs, graph, wrap-up

- [ ] **Step 1:** `docs/TODO.md`: mark the 2026-07-10 "FULL workforce detail" decision **Superseded (2026-07-11)** with a pointer to the new entry; add the new resolved decision (aggregates-only context, 25-row tool caps, 3 rounds, chosen over vector RAG).
`docs/TECHNICAL.md`: update the `/assistant/chat` row — standing context is aggregate counts only; specifics fetched via read tools; personal data leaves the server only for the queried slice.

- [ ] **Step 2:** `./mvnw test` green → graph refresh → commit docs.
