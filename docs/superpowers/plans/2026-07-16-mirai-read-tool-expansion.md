# Mirai Read-Tool Expansion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give Mirai four new read tools — `get_skill_gaps`, `list_expiring_certifications`, `get_workforce_summary`, `list_bench_aging` — as thin text formatters over existing service math, plus one suggestion-chip swap.

**Architecture:** Each tool follows the established pipeline: a formatter method in `AssistantContextBuilder` (which gains `SkillGapService` + `DashboardService` as constructor deps), a function declaration in `GeminiHttpClient`, a dispatch case in `AssistantService.executeReadTool`, and a mention in the standing prompt. One small extraction: the expiring-cert filter moves from inside `DashboardService.summary()` to a public `DashboardService.expiringCerts(int withinDays)` so the tool and the dashboard share one implementation. No other math is written — everything formats DTOs that `SkillGapService.dashboardPanel()` and `DashboardService.summary()` already produce.

**Tech Stack:** Spring Boot 3.5 / Java 21, JUnit 5 + AssertJ + MockMvc (tests extend `ApiTestBase`, which gives seed helpers and repositories), React 18 (one chip swap only).

**Spec:** `docs/superpowers/specs/2026-07-16-mirai-read-tool-expansion-design.md`

**Rules that apply to every task** (from `AGENTS.md`):
- TDD: write the failing test, watch it fail for the right reason, then minimal code.
- Full suite green before every commit: `./mvnw test`. If Spotless complains, `./mvnw spotless:apply` and rerun.
- Commit messages are plain sentences ending with `Co-Authored-By: <your model> <noreply@anthropic.com>`.

---

### Task 1: `get_skill_gaps` tool

**Files:**
- Modify: `src/main/java/com/softility/omivertex/service/AssistantContextBuilder.java` (new dep + formatter + prompt mention)
- Modify: `src/main/java/com/softility/omivertex/service/AssistantService.java` (dispatch case)
- Modify: `src/main/java/com/softility/omivertex/service/GeminiHttpClient.java` (READ_TOOLS + declaration)
- Test: `src/test/java/com/softility/omivertex/api/AssistantContextBuilderTest.java`
- Test: `src/test/java/com/softility/omivertex/api/AssistantApiTest.java`

- [ ] **Step 1: Write the failing formatter tests**

Add to `AssistantContextBuilderTest.java` (the existing `seedWorkforce()` creates an open "Java Dev" seat requiring Java @INTERMEDIATE with headcount 1; Priya holds Java at ADVANCE but is allocated, Rahul is unrated — so demand 1, bench supply 0, total holders 1, gap 1):

```java
@Test
void skillGaps_reportsDemandBenchSupplyHoldersAndGap() {
    seedWorkforce();
    String result = builder.skillGaps();
    assertThat(result).contains("Java (Backend)");
    assertThat(result).contains("demand 1");
    assertThat(result).contains("bench supply 0"); // Priya is allocated; Rahul is unrated
    assertThat(result).contains("total holders 1");
    assertThat(result).contains("gap 1");
}

@Test
void skillGaps_emptyWhenNoOpenDemand() {
    associate("Rahul Verma", "rahul@softility.com", WorkMode.ONSHORE); // no positions at all
    assertThat(builder.skillGaps()).contains("No open positions demand any skills");
}

@Test
void standingContext_advertisesSkillGapTool() {
    seedWorkforce();
    assertThat(builder.build()).contains("get_skill_gaps");
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw test -Dtest=AssistantContextBuilderTest`
Expected: COMPILATION ERROR — `skillGaps()` does not exist. That is the right failure.

- [ ] **Step 3: Implement the formatter**

In `AssistantContextBuilder.java`:

(a) Add fields and constructor params (keep the existing ones; two new lines each):

```java
private final SkillGapService skillGapService;
private final DashboardService dashboardService;
```

Constructor becomes (full signature — `DashboardService` is added now so Task 2–4 don't touch the constructor again):

```java
public AssistantContextBuilder(AssociateRepository associates, AllocationRepository allocations,
                               AssociateSkillRepository associateSkills, CertificationRepository certifications,
                               OpenPositionRepository positions, PositionSkillRepository positionSkills,
                               ClientRepository clients, ProjectRepository projects,
                               SkillGapService skillGapService, DashboardService dashboardService) {
    this.associates = associates;
    this.allocations = allocations;
    this.associateSkills = associateSkills;
    this.certifications = certifications;
    this.positions = positions;
    this.positionSkills = positionSkills;
    this.clients = clients;
    this.projects = projects;
    this.skillGapService = skillGapService;
    this.dashboardService = dashboardService;
}
```

Add the import:

```java
import com.softility.omivertex.web.dto.DashboardSummaryResponse;
```

(b) Add the formatter (place it after `listProjects` / `projectDetail`, before the "shared row fragments" section):

```java
/** Read tool: skill supply vs demand, worst gap first (SkillGapService owns the math). */
public String skillGaps() {
    List<DashboardSummaryResponse.SkillGap> gaps = skillGapService.dashboardPanel();
    if (gaps.isEmpty()) {
        return "No open positions demand any skills right now.";
    }
    return gaps.stream()
            .map(g -> "- " + g.skillName() + " (" + g.category() + ") · demand " + g.demand()
                    + " · bench supply " + g.benchSupply() + " · total holders " + g.totalSupply()
                    + " · gap " + g.gap())
            .collect(Collectors.joining("\n"));
}
```

(No extra cap needed: `dashboardPanel()` caps itself at 20, under `MAX_TOOL_ROWS`.)

(c) In `build()`, extend the tool list. The fragment

```java
+ "list_clients, list_projects) "
```

becomes

```java
+ "list_clients, list_projects, get_skill_gaps) "
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./mvnw test -Dtest=AssistantContextBuilderTest`
Expected: PASS (all tests, including the pre-existing ones).

- [ ] **Step 5: Write the failing dispatch test**

Add to `AssistantApiTest.java`:

```java
@Test
void chat_dispatchesSkillGapsTool() throws Exception {
    var acme = client("Acme Corp");
    var proj = project("ACM-100", "Storefront Revamp", acme);
    var java = skill("Backend", "Java");
    var position = new com.softility.omivertex.domain.OpenPosition();
    position.setTitle("Java Dev");
    position.setProject(proj);
    openPositionRepository.save(position);
    var req = new com.softility.omivertex.domain.PositionSkill();
    req.setPosition(position);
    req.setSkill(java);
    req.setRequired(true);
    positionSkillRepository.save(req);

    when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any()))
            .thenAnswer(inv -> {
                GeminiClient.ToolExecutor ex = inv.getArgument(3);
                return new GeminiClient.AssistantReply(ex.execute("get_skill_gaps", Map.of()), null);
            });

    asyncPerform(post("/api/v1/assistant/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"message":"what are our skill gaps?","history":[]}"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reply", containsString("Java (Backend)")))
            .andExpect(jsonPath("$.reply", containsString("gap 1")));
}
```

- [ ] **Step 6: Run it to verify it fails for the right reason**

Run: `./mvnw test -Dtest=AssistantApiTest#chat_dispatchesSkillGapsTool`
Expected: FAIL — the reply is `Unknown tool: get_skill_gaps` (the dispatch case doesn't exist yet).

- [ ] **Step 7: Wire dispatch and declaration**

(a) `AssistantService.executeReadTool` — add a case to the switch:

```java
case "get_skill_gaps" -> contextBuilder.skillGaps();
```

(b) `GeminiHttpClient.READ_TOOLS` — add `"get_skill_gaps"` to the `Set.of(...)`.

(c) `GeminiHttpClient.FUNCTION_DECLARATIONS` — add to the `List.of(...)`:

```java
Map.of("name", "get_skill_gaps",
        "description", "Skill supply vs demand: for each skill required by open positions,"
                + " how many seats demand it, bench supply, total holders, and the gap"
                + " (positive = hire or train). Use when asked about skill gaps, shortages,"
                + " or hiring/training needs.",
        "parameters", Map.of("type", "object", "properties", Map.of())),
```

- [ ] **Step 8: Run the full suite**

Run: `./mvnw test`
Expected: PASS (Spotless + ArchUnit included). If Spotless fails: `./mvnw spotless:apply`, rerun.

- [ ] **Step 9: Commit**

```bash
git add -A src/main src/test
git commit -m "Give Mirai a get_skill_gaps read tool grounded in SkillGapService

Co-Authored-By: <your model> <noreply@anthropic.com>"
```

---

### Task 2: `list_expiring_certifications` tool (+ `expiringCerts` extraction)

**Files:**
- Modify: `src/main/java/com/softility/omivertex/service/DashboardService.java` (extract public `expiringCerts`)
- Modify: `src/main/java/com/softility/omivertex/service/AssistantContextBuilder.java` (formatter + prompt mention)
- Modify: `src/main/java/com/softility/omivertex/service/AssistantService.java` (dispatch case)
- Modify: `src/main/java/com/softility/omivertex/service/GeminiHttpClient.java` (READ_TOOLS + declaration)
- Test: `src/test/java/com/softility/omivertex/api/AssistantContextBuilderTest.java`
- Test: `src/test/java/com/softility/omivertex/api/AssistantApiTest.java`

- [ ] **Step 1: Write the failing formatter tests**

Add to `AssistantContextBuilderTest.java` — first this private helper at the bottom of the class:

```java
private void certification(com.softility.omivertex.domain.Associate holder, String name,
                           LocalDate expiry) {
    var cert = new com.softility.omivertex.domain.Certification();
    cert.setAssociate(holder);
    cert.setName(name);
    cert.setExpiryDate(expiry);
    certificationRepository.save(cert);
}
```

then the tests:

```java
@Test
void expiringCertifications_windowIsInclusive_excludesBeyondAndExpired() {
    var holder = associate("Ravi Kumar", "ravi@softility.com", WorkMode.OFFSHORE);
    certification(holder, "AWS Solutions Architect", LocalDate.now().plusDays(60)); // boundary day
    certification(holder, "Azure Administrator", LocalDate.now().plusDays(61));     // beyond window
    certification(holder, "GCP Engineer", LocalDate.now().minusDays(1));            // already expired

    String result = builder.expiringCertifications(60);

    assertThat(result).contains("Ravi Kumar — AWS Solutions Architect");
    assertThat(result).contains("(in 60 days)");
    assertThat(result).doesNotContain("Azure Administrator");
    assertThat(result).doesNotContain("GCP Engineer");
}

@Test
void expiringCertifications_emptyStateNamesTheWindow() {
    assertThat(builder.expiringCertifications(30))
            .contains("No certifications expire within 30 days");
}

@Test
void expiringCertifications_capsRowsWithOverflowLine() {
    var holder = associate("Ravi Kumar", "ravi@softility.com", WorkMode.OFFSHORE);
    for (int i = 1; i <= 27; i++) {
        certification(holder, "Cert " + i, LocalDate.now().plusDays(i));
    }
    // 27 upcoming certs, MAX_TOOL_ROWS = 25 -> shared overflow line
    assertThat(builder.expiringCertifications(90)).contains("…and 2 more");
}

@Test
void standingContext_advertisesCertExpiryTool() {
    seedWorkforce();
    assertThat(builder.build()).contains("list_expiring_certifications");
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw test -Dtest=AssistantContextBuilderTest`
Expected: COMPILATION ERROR — `expiringCertifications(int)` does not exist.

- [ ] **Step 3: Extract `expiringCerts` in `DashboardService`**

Add this public method (near `summary()`):

```java
/**
 * Certifications expiring within {@code withinDays} of today, soonest first.
 * Upcoming only — already-expired certs are excluded. Shared by the dashboard
 * radar (fixed {@link #CERT_EXPIRY_HORIZON_DAYS}) and the assistant tool
 * (caller-chosen window), so the filter exists exactly once.
 */
public List<DashboardSummaryResponse.ExpiringCert> expiringCerts(int withinDays) {
    LocalDate today = LocalDate.now();
    LocalDate limit = today.plusDays(withinDays);
    return certificationRepository.findAllWithAssociate().stream()
            .filter(c -> c.getExpiryDate() != null
                    && !c.getExpiryDate().isBefore(today)
                    && !c.getExpiryDate().isAfter(limit))
            .sorted(Comparator.comparing(com.softility.omivertex.domain.Certification::getExpiryDate))
            .map(c -> new DashboardSummaryResponse.ExpiringCert(c.getId(),
                    c.getAssociate().getId(), c.getAssociate().getName(),
                    c.getName(), c.getExpiryDate(), ChronoUnit.DAYS.between(today, c.getExpiryDate())))
            .toList();
}
```

Then in `summary()`, replace the inline block

```java
// expiring certifications: certifications expiring within the horizon
LocalDate certExpiryLimit = today.plusDays(CERT_EXPIRY_HORIZON_DAYS);
List<DashboardSummaryResponse.ExpiringCert> expiringCerts = certificationRepository.findAllWithAssociate().stream()
        .filter(c -> c.getExpiryDate() != null
                && !c.getExpiryDate().isBefore(today)
                && !c.getExpiryDate().isAfter(certExpiryLimit))
        .sorted(Comparator.comparing(com.softility.omivertex.domain.Certification::getExpiryDate))
        .map(c -> new DashboardSummaryResponse.ExpiringCert(c.getId(),
                c.getAssociate().getId(), c.getAssociate().getName(),
                c.getName(), c.getExpiryDate(), ChronoUnit.DAYS.between(today, c.getExpiryDate())))
        .toList();
```

with

```java
// expiring certifications: shared filter lives in expiringCerts(int)
List<DashboardSummaryResponse.ExpiringCert> expiringCerts = expiringCerts(CERT_EXPIRY_HORIZON_DAYS);
```

(Dashboard behavior is unchanged; the existing dashboard tests pin that.)

- [ ] **Step 4: Implement the formatter**

Add to `AssistantContextBuilder.java`:

```java
/** Read tool: certifications expiring in the window, soonest first, capped. */
public String expiringCertifications(int withinDays) {
    List<DashboardSummaryResponse.ExpiringCert> certs = dashboardService.expiringCerts(withinDays);
    if (certs.isEmpty()) {
        return "No certifications expire within " + withinDays + " days.";
    }
    StringBuilder sb = new StringBuilder();
    for (DashboardSummaryResponse.ExpiringCert c : certs.stream().limit(MAX_TOOL_ROWS).toList()) {
        sb.append("- ").append(c.associateName()).append(" — ").append(c.name())
          .append(", expires ").append(c.expiryDate())
          .append(" (in ").append(c.daysLeft()).append(" days)\n");
    }
    appendOverflow(sb, certs.size());
    return sb.toString();
}
```

And in `build()`, extend the tool list fragment to

```java
+ "list_clients, list_projects, get_skill_gaps, list_expiring_certifications) "
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./mvnw test -Dtest=AssistantContextBuilderTest`
Expected: PASS.

- [ ] **Step 6: Write the failing dispatch test**

Add to `AssistantApiTest.java` — it exercises both the explicit arg and the 90-day default:

```java
@Test
void chat_dispatchesExpiringCertificationsTool_defaultsTo90Days() throws Exception {
    var holder = associate("Ravi Kumar", "ravi@softility.com", WorkMode.OFFSHORE);
    var cert = new com.softility.omivertex.domain.Certification();
    cert.setAssociate(holder);
    cert.setName("AWS Solutions Architect");
    cert.setExpiryDate(java.time.LocalDate.now().plusDays(80)); // inside 90, outside 30
    certificationRepository.save(cert);

    when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any()))
            .thenAnswer(inv -> {
                GeminiClient.ToolExecutor ex = inv.getArgument(3);
                String defaulted = ex.execute("list_expiring_certifications", Map.of());
                String narrow = ex.execute("list_expiring_certifications", Map.of("withinDays", 30));
                return new GeminiClient.AssistantReply(defaulted + "|" + narrow, null);
            });

    asyncPerform(post("/api/v1/assistant/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"message":"whose certifications expire soon?","history":[]}"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reply", containsString("AWS Solutions Architect")))
            .andExpect(jsonPath("$.reply", containsString("No certifications expire within 30 days")));
}
```

- [ ] **Step 7: Run it to verify it fails for the right reason**

Run: `./mvnw test -Dtest=AssistantApiTest#chat_dispatchesExpiringCertificationsTool_defaultsTo90Days`
Expected: FAIL — reply contains `Unknown tool: list_expiring_certifications`.

- [ ] **Step 8: Wire dispatch and declaration**

(a) `AssistantService.executeReadTool` — add:

```java
case "list_expiring_certifications" -> contextBuilder.expiringCertifications(
        intOrDefault(args.get("withinDays"), DashboardService.CERT_EXPIRY_HORIZON_DAYS));
```

(`CERT_EXPIRY_HORIZON_DAYS` is package-private static in the same package — no magic 90.)

(b) `GeminiHttpClient.READ_TOOLS` — add `"list_expiring_certifications"`.

(c) `GeminiHttpClient.FUNCTION_DECLARATIONS` — add:

```java
Map.of("name", "list_expiring_certifications",
        "description", "Certifications expiring soon across the whole workforce, soonest"
                + " first. Use when asked whose certifications expire or need renewal.",
        "parameters", Map.of("type", "object",
                "properties", Map.of("withinDays", Map.of("type", "integer",
                        "description", "look-ahead window in days; default 90")))),
```

- [ ] **Step 9: Run the full suite**

Run: `./mvnw test`
Expected: PASS — including the dashboard tests, which pin that the `expiringCerts` extraction changed nothing.

- [ ] **Step 10: Commit**

```bash
git add -A src/main src/test
git commit -m "Give Mirai a list_expiring_certifications tool; extract the shared cert-expiry filter

Co-Authored-By: <your model> <noreply@anthropic.com>"
```

---

### Task 3: `get_workforce_summary` tool

**Files:**
- Modify: `src/main/java/com/softility/omivertex/service/AssistantContextBuilder.java` (formatter + prompt mention)
- Modify: `src/main/java/com/softility/omivertex/service/AssistantService.java` (dispatch case)
- Modify: `src/main/java/com/softility/omivertex/service/GeminiHttpClient.java` (READ_TOOLS + declaration)
- Test: `src/test/java/com/softility/omivertex/api/AssistantContextBuilderTest.java`
- Test: `src/test/java/com/softility/omivertex/api/AssistantApiTest.java`

- [ ] **Step 1: Write the failing formatter tests**

Add to `AssistantContextBuilderTest.java`:

```java
@Test
void workforceSummary_coversKpisBucketsTrendAndForecast() {
    seedWorkforce(); // Priya 100% billable, Rahul benched -> 2 active, utilization 50%
    var alloc = allocationRepository.findAll().get(0);
    alloc.setEndDate(LocalDate.now().plusDays(10)); // roll-off inside the +30d forecast window
    allocationRepository.save(alloc);

    String result = builder.workforceSummary();

    assertThat(result).contains("Active associates: 2");
    assertThat(result).contains("bench 1");
    assertThat(result).contains("utilization: 50%");
    assertThat(result).contains("Bench aging:");
    assertThat(result).contains("Staffing trend");
    assertThat(result).contains("Utilization forecast");
    assertThat(result).contains("Today: 50%");
    // the scheduled roll-off surfaces as a named forecast driver
    assertThat(result).contains("ROLL_OFF Priya Sharma (Storefront Revamp)");
}

@Test
void workforceSummary_leavesSlicesToTheirOwnTools() {
    seedWorkforce();
    String result = builder.workforceSummary();
    // open-position, cert, and skill-gap rows belong to their dedicated tools
    assertThat(result).doesNotContain("Java Dev");
    assertThat(result).doesNotContain("must-have");
    assertThat(result).doesNotContain("gap ");
}

@Test
void standingContext_advertisesWorkforceSummaryTool() {
    seedWorkforce();
    assertThat(builder.build()).contains("get_workforce_summary");
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw test -Dtest=AssistantContextBuilderTest`
Expected: COMPILATION ERROR — `workforceSummary()` does not exist.

- [ ] **Step 3: Implement the formatter**

Add to `AssistantContextBuilder.java`:

```java
/** Read tool: org-health KPIs, bench aging, six-month trend, and utilization forecast. */
public String workforceSummary() {
    DashboardSummaryResponse s = dashboardService.summary();
    StringBuilder sb = new StringBuilder();
    sb.append("Active associates: ").append(s.totalAssociates())
      .append(" · billable ").append(s.billableCount())
      .append(" · non-billable ").append(s.nonBillableCount())
      .append(" · bench ").append(s.benchCount())
      .append(" · onshore ").append(s.onshoreCount())
      .append(" · offshore ").append(s.offshoreCount()).append("\n");
    sb.append("Clients: ").append(s.totalClients())
      .append(" · active projects: ").append(s.activeProjects())
      .append(" · open positions: ").append(s.openPositions())
      .append(" · utilization: ").append(s.utilizationPercent()).append("%")
      .append(" · exits last 12 months: ").append(s.exitsLast12Months()).append("\n");
    DashboardSummaryResponse.BenchAging aging = s.benchAging();
    sb.append("Bench aging: ").append(aging.days0to30()).append(" ≤30d · ")
      .append(aging.days31to60()).append(" 31–60d · ")
      .append(aging.days60plus()).append(" >60d\n");
    sb.append("Staffing trend (allocated/billable per month): ").append(s.staffingTrend().stream()
            .map(t -> t.month() + " " + t.total() + "/" + t.billable())
            .collect(Collectors.joining(", "))).append("\n");
    sb.append("Utilization forecast:\n");
    for (DashboardSummaryResponse.ForecastPoint p : s.utilizationForecast()) {
        sb.append("- ").append(p.label()).append(": ").append(p.percent()).append("%");
        if (p.deltaPoints() != 0) {
            sb.append(" (").append(p.deltaPoints() > 0 ? "+" : "")
              .append(p.deltaPoints()).append(" vs today)");
        }
        if (!p.drivers().isEmpty()) {
            sb.append(" — ").append(p.drivers().stream()
                    .map(d -> d.kind() + " " + d.associateName()
                            + (d.projectName() == null ? "" : " (" + d.projectName() + ")")
                            + " " + d.date())
                    .collect(Collectors.joining("; ")));
            if (p.omittedDrivers() > 0) {
                sb.append(" …and ").append(p.omittedDrivers()).append(" more");
            }
        }
        sb.append("\n");
    }
    return sb.toString();
}
```

(The DTO's roll-off, expiring-cert, skill-gap, and client-headcount sections are deliberately not formatted — per the spec, those slices belong to their own tools.)

And in `build()`, extend the tool list fragment to

```java
+ "list_clients, list_projects, get_skill_gaps, list_expiring_certifications, "
+ "get_workforce_summary) "
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./mvnw test -Dtest=AssistantContextBuilderTest`
Expected: PASS.

- [ ] **Step 5: Write the failing dispatch test**

Add to `AssistantApiTest.java`:

```java
@Test
void chat_dispatchesWorkforceSummaryTool() throws Exception {
    associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE); // 1 active, benched

    when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any()))
            .thenAnswer(inv -> {
                GeminiClient.ToolExecutor ex = inv.getArgument(3);
                return new GeminiClient.AssistantReply(
                        ex.execute("get_workforce_summary", Map.of()), null);
            });

    asyncPerform(post("/api/v1/assistant/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"message":"how healthy is the org?","history":[]}"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reply", containsString("Active associates: 1")))
            .andExpect(jsonPath("$.reply", containsString("Utilization forecast")));
}
```

- [ ] **Step 6: Run it to verify it fails for the right reason**

Run: `./mvnw test -Dtest=AssistantApiTest#chat_dispatchesWorkforceSummaryTool`
Expected: FAIL — reply contains `Unknown tool: get_workforce_summary`.

- [ ] **Step 7: Wire dispatch and declaration**

(a) `AssistantService.executeReadTool` — add:

```java
case "get_workforce_summary" -> contextBuilder.workforceSummary();
```

(b) `GeminiHttpClient.READ_TOOLS` — add `"get_workforce_summary"`.

(c) `GeminiHttpClient.FUNCTION_DECLARATIONS` — add:

```java
Map.of("name", "get_workforce_summary",
        "description", "Org-health snapshot: headcounts, billable/non-billable/bench split,"
                + " onshore/offshore, utilization %, bench-aging buckets, exits in the last"
                + " 12 months, the six-month staffing trend, and the 30/60/90-day utilization"
                + " forecast with the events driving it. Use for overall health, utilization,"
                + " or trend questions.",
        "parameters", Map.of("type", "object", "properties", Map.of())),
```

- [ ] **Step 8: Run the full suite**

Run: `./mvnw test`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add -A src/main src/test
git commit -m "Give Mirai a get_workforce_summary tool over the dashboard KPIs and forecast

Co-Authored-By: <your model> <noreply@anthropic.com>"
```

---

### Task 4: `list_bench_aging` tool

**Files:**
- Modify: `src/main/java/com/softility/omivertex/service/AssistantContextBuilder.java` (formatter + prompt mention)
- Modify: `src/main/java/com/softility/omivertex/service/AssistantService.java` (dispatch case)
- Modify: `src/main/java/com/softility/omivertex/service/GeminiHttpClient.java` (READ_TOOLS + declaration)
- Test: `src/test/java/com/softility/omivertex/api/AssistantContextBuilderTest.java`
- Test: `src/test/java/com/softility/omivertex/api/AssistantApiTest.java`

- [ ] **Step 1: Write the failing formatter tests**

Add to `AssistantContextBuilderTest.java`:

```java
@Test
void benchAging_bucketHeadline_longestBenchedFirst() {
    var acme = client("Acme Corp");
    var proj = project("ACM-100", "Storefront Revamp", acme);
    var longBench = associate("Anil Kumar", "anil@softility.com", WorkMode.OFFSHORE);
    var endedLongAgo = allocation(longBench, proj, true);
    endedLongAgo.setStartDate(LocalDate.now().minusDays(200));
    endedLongAgo.setEndDate(LocalDate.now().minusDays(74)); // 74 days on bench
    allocationRepository.save(endedLongAgo);
    var freshBench = associate("Meera Iyer", "meera@softility.com", WorkMode.ONSHORE);
    var endedRecently = allocation(freshBench, proj, true);
    endedRecently.setStartDate(LocalDate.now().minusDays(60));
    endedRecently.setEndDate(LocalDate.now().minusDays(5)); // 5 days on bench
    allocationRepository.save(endedRecently);

    String result = builder.benchAging();

    assertThat(result).contains("1 ≤30d").contains("0 31–60d").contains("1 >60d");
    assertThat(result).contains("74 days on bench");
    assertThat(result.indexOf("Anil Kumar")).isLessThan(result.indexOf("Meera Iyer"));
}

@Test
void benchAging_emptyWhenEveryoneIsAllocated() {
    var acme = client("Acme Corp");
    var proj = project("ACM-100", "Storefront Revamp", acme);
    var busy = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
    allocation(busy, proj, true);

    assertThat(builder.benchAging()).contains("No one is on the bench");
}

@Test
void benchAging_capsRowsWithOverflowLine() {
    for (int i = 1; i <= 27; i++) {
        associate("Bench Person " + i, "bench" + i + "@softility.com", WorkMode.OFFSHORE);
    }
    // 27 benched people, MAX_TOOL_ROWS = 25 -> shared overflow line
    assertThat(builder.benchAging()).contains("…and 2 more");
}

@Test
void standingContext_advertisesBenchAgingTool() {
    seedWorkforce();
    assertThat(builder.build()).contains("list_bench_aging");
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw test -Dtest=AssistantContextBuilderTest`
Expected: COMPILATION ERROR — `benchAging()` does not exist.

- [ ] **Step 3: Implement the formatter**

Add to `AssistantContextBuilder.java`:

```java
/** Read tool: bench bucket counts + the bench roster, longest-benched first, capped. */
public String benchAging() {
    DashboardSummaryResponse s = dashboardService.summary();
    List<DashboardSummaryResponse.BenchAssociate> bench = s.benchAssociates();
    if (bench.isEmpty()) {
        return "No one is on the bench.";
    }
    DashboardSummaryResponse.BenchAging aging = s.benchAging();
    StringBuilder sb = new StringBuilder();
    sb.append("Bench: ").append(bench.size()).append(" people — ")
      .append(aging.days0to30()).append(" ≤30d · ")
      .append(aging.days31to60()).append(" 31–60d · ")
      .append(aging.days60plus()).append(" >60d\n");
    for (DashboardSummaryResponse.BenchAssociate b : bench.stream().limit(MAX_TOOL_ROWS).toList()) {
        sb.append("- ").append(b.name()).append(" · ")
          .append(b.designation() == null ? "no designation" : b.designation())
          .append(" · ").append(b.benchDays()).append(" days on bench\n");
    }
    appendOverflow(sb, bench.size());
    return sb.toString();
}
```

(`benchAssociates` arrives already sorted longest-first from `DashboardService`.)

And in `build()`, extend the tool list fragment to its final form:

```java
+ "list_clients, list_projects, get_skill_gaps, list_expiring_certifications, "
+ "get_workforce_summary, list_bench_aging) "
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./mvnw test -Dtest=AssistantContextBuilderTest`
Expected: PASS.

- [ ] **Step 5: Write the failing dispatch test**

Add to `AssistantApiTest.java`:

```java
@Test
void chat_dispatchesBenchAgingTool() throws Exception {
    associate("Rahul Verma", "rahul@softility.com", WorkMode.ONSHORE); // benched

    when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any()))
            .thenAnswer(inv -> {
                GeminiClient.ToolExecutor ex = inv.getArgument(3);
                return new GeminiClient.AssistantReply(ex.execute("list_bench_aging", Map.of()), null);
            });

    asyncPerform(post("/api/v1/assistant/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"message":"who has been on the bench longest?","history":[]}"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reply", containsString("Rahul Verma")))
            .andExpect(jsonPath("$.reply", containsString("days on bench")));
}
```

- [ ] **Step 6: Run it to verify it fails for the right reason**

Run: `./mvnw test -Dtest=AssistantApiTest#chat_dispatchesBenchAgingTool`
Expected: FAIL — reply contains `Unknown tool: list_bench_aging`.

- [ ] **Step 7: Wire dispatch and declaration**

(a) `AssistantService.executeReadTool` — add:

```java
case "list_bench_aging" -> contextBuilder.benchAging();
```

(b) `GeminiHttpClient.READ_TOOLS` — add `"list_bench_aging"`.

(c) `GeminiHttpClient.FUNCTION_DECLARATIONS` — add:

```java
Map.of("name", "list_bench_aging",
        "description", "Everyone on the bench sorted longest-benched first with days on"
                + " bench, plus aging bucket counts. Use when asked who has been on the"
                + " bench longest or how the bench is aging.",
        "parameters", Map.of("type", "object", "properties", Map.of())),
```

- [ ] **Step 8: Run the full suite**

Run: `./mvnw test`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add -A src/main src/test
git commit -m "Give Mirai a list_bench_aging tool over the dashboard bench roster

Co-Authored-By: <your model> <noreply@anthropic.com>"
```

---

### Task 5: Suggestion-chip swap

**Files:**
- Modify: `frontend/src/components/AssistantChat.jsx:7-13` (the `SUGGESTIONS` array)

(No JS test infra exists — the guardrails here are Prettier + ESLint via the build.)

- [ ] **Step 1: Swap the chip**

In the `SUGGESTIONS` array, replace

```js
'Who matches our open positions?',
```

with

```js
'Whose certifications expire soon?',
```

("Who matches our open positions?" was redundant with "Which open positions have no bench match?"; the new chip advertises the new cert tool. "Summarize our biggest skill gaps." stays — it finally works.)

- [ ] **Step 2: Format and build**

Run: `cd frontend && npm run format && npm run build`
Expected: build succeeds (Prettier + ESLint clean).

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/AssistantChat.jsx
git commit -m "Swap a Mirai suggestion chip to advertise the certification-expiry tool

Co-Authored-By: <your model> <noreply@anthropic.com>"
```

---

### Task 6: Docs, graph, final verification

**Files:**
- Modify: `docs/TECHNICAL.md` (assistant endpoint row ~line 197 and assistant section ~line 148)

- [ ] **Step 1: Update `docs/TECHNICAL.md`**

(a) In the `/assistant/chat` endpoint row (~line 197), extend the read-tool list

```
(`search_associates`, `get_associate_detail`, `get_project_detail`, `list_rolloffs`, `list_open_positions`, `get_position_matches`, `list_clients`, `list_projects`; ≤25 rows each, ≤3 tool rounds)
```

to

```
(`search_associates`, `get_associate_detail`, `get_project_detail`, `list_rolloffs`, `list_open_positions`, `get_position_matches`, `list_clients`, `list_projects`, `get_skill_gaps`, `list_expiring_certifications`, `get_workforce_summary`, `list_bench_aging`; ≤25 rows each, ≤3 tool rounds)
```

(b) In the numbered "AI assistant" item (~line 148), extend the parenthetical history note `(2026-07-10; context narrowed 2026-07-11, enumeration tools added 2026-07-15)` with `, dashboard-grounded tools added 2026-07-16` and add one sentence to the item:

```
Four tools reuse dashboard math instead of recomputing it: `get_skill_gaps`
(`SkillGapService.dashboardPanel()`), `list_expiring_certifications`
(`DashboardService.expiringCerts(withinDays)`, default 90, shared with the
dashboard radar), and `get_workforce_summary` / `list_bench_aging`
(`DashboardService.summary()`).
```

- [ ] **Step 2: Refresh the knowledge graph**

Run: `$(cat graphify-out/.graphify_python) -c "from graphify.watch import _rebuild_code; from pathlib import Path; _rebuild_code(Path('.'))"`

- [ ] **Step 3: Final verification**

Run: `./mvnw test` — expected PASS.
Run: `cd frontend && npm run format:check && npm run build` — expected clean.

- [ ] **Step 4: Commit docs + graph**

```bash
git add docs/TECHNICAL.md graphify-out
git commit -m "Document Mirai's four dashboard-grounded read tools; refresh the graph

Co-Authored-By: <your model> <noreply@anthropic.com>"
```

- [ ] **Step 5: Delete this plan (plans are disposable scaffolding)**

```bash
git rm docs/superpowers/plans/2026-07-16-mirai-read-tool-expansion.md
git commit -m "Remove the merged Mirai read-tool expansion plan

Co-Authored-By: <your model> <noreply@anthropic.com>"
```
