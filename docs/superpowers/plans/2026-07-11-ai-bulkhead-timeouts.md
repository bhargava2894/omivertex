# AI Bulkhead + Timeouts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Gemini calls get connect/read timeouts and run on a dedicated 4-thread bulkhead executor; the three AI endpoints go async so slow AI can never starve the servlet pool, with a fast 503 when the AI pool is saturated.

**Architecture:** New `AiExecutor` component (ThreadPoolTaskExecutor 4/4, queue 8, abort → `ServiceUnavailableException` → 503 via `GlobalExceptionHandler`). `AssistantController.chat`, `ResumeController.parseResume`, `MeController.parseResume` return `CompletableFuture` supplied on it. `GeminiHttpClient` builds its `RestClient` with `SimpleClientHttpRequestFactory` timeouts bound from two new `Duration` properties. HTTP contract unchanged; frontend untouched.

**Tech Stack:** Spring Boot 3.5 async MVC (`CompletableFuture` return values, MockMvc `asyncDispatch`), `ThreadPoolTaskExecutor`.

**Branch:** `feature/ai-bulkhead`. Spec: `docs/superpowers/specs/2026-07-11-ai-bulkhead-timeouts-design.md`.

---

### Task 1: `ServiceUnavailableException` + `AiExecutor` (unit-first)

**Files:**
- Create: `src/test/java/com/softility/omivertex/service/AiExecutorTest.java`
- Create: `src/main/java/com/softility/omivertex/web/error/ServiceUnavailableException.java`
- Create: `src/main/java/com/softility/omivertex/service/AiExecutor.java`
- Modify: `src/main/java/com/softility/omivertex/web/error/GlobalExceptionHandler.java`

- [ ] **Step 1: Failing unit test**

```java
package com.softility.omivertex.service;

import com.softility.omivertex.web.error.ServiceUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiExecutorTest {

    private final AiExecutor executor = new AiExecutor();

    @AfterEach
    void shutDown() {
        executor.shutdown();
    }

    @Test
    void submit_runsTheTaskOffTheCallerThread() throws Exception {
        String callerThread = Thread.currentThread().getName();
        CompletableFuture<String> result = executor.submit(() -> Thread.currentThread().getName());
        assertThat(result.get()).isNotEqualTo(callerThread).startsWith("ai-");
    }

    @Test
    void submit_whenPoolAndQueueAreFull_throwsServiceUnavailable() {
        CountDownLatch release = new CountDownLatch(1);
        try {
            // fill all threads and the whole queue with blocked tasks
            for (int i = 0; i < AiExecutor.POOL_SIZE + AiExecutor.QUEUE_CAPACITY; i++) {
                executor.submit(() -> {
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return null;
                });
            }
            assertThatThrownBy(() -> executor.submit(() -> "overflow"))
                    .isInstanceOf(ServiceUnavailableException.class)
                    .hasMessageContaining("busy");
        } finally {
            release.countDown();
        }
    }
}
```

Run: `./mvnw test -Dtest=AiExecutorTest` → COMPILE FAILURE (classes missing) = red.

- [ ] **Step 2: Implement**

`ServiceUnavailableException.java` (mirror `ConflictException`'s shape):

```java
package com.softility.omivertex.web.error;

/** 503 — a bounded resource (e.g. the AI executor) is saturated; retry shortly. */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) {
        super(message);
    }
}
```

`AiExecutor.java`:

```java
package com.softility.omivertex.service;

import com.softility.omivertex.web.error.ServiceUnavailableException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

/**
 * Bulkhead for all Gemini work: AI calls run on this small dedicated pool so a
 * slow or hung upstream can never occupy servlet request threads, and a
 * saturated pool fails fast (503) instead of queueing unboundedly.
 */
@Component
public class AiExecutor {

    /** Concurrent Gemini calls; small on purpose — the upstream is the bottleneck. */
    static final int POOL_SIZE = 4;
    /** Requests allowed to wait for a thread before we start rejecting. */
    static final int QUEUE_CAPACITY = 8;

    private final ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();

    public AiExecutor() {
        pool.setCorePoolSize(POOL_SIZE);
        pool.setMaxPoolSize(POOL_SIZE);
        pool.setQueueCapacity(QUEUE_CAPACITY);
        pool.setThreadNamePrefix("ai-");
        pool.initialize();
    }

    public <T> CompletableFuture<T> submit(Supplier<T> task) {
        try {
            return CompletableFuture.supplyAsync(task, pool);
        } catch (RejectedExecutionException e) {
            throw new ServiceUnavailableException(
                    "The AI assistant is busy right now — try again shortly");
        }
    }

    public void shutdown() {
        pool.shutdown();
    }
}
```

`GlobalExceptionHandler.java` — add next to the other handlers:

```java
@ExceptionHandler(ServiceUnavailableException.class)
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public ApiError handleServiceUnavailable(ServiceUnavailableException ex) {
    return ApiError.of(ex.getMessage());
}
```

- [ ] **Step 3: Green + commit**

Run: `./mvnw test -Dtest=AiExecutorTest` → PASS.

```bash
git add src/main/java/com/softility/omivertex/service/AiExecutor.java \
        src/main/java/com/softility/omivertex/web/error/ServiceUnavailableException.java \
        src/main/java/com/softility/omivertex/web/error/GlobalExceptionHandler.java \
        src/test/java/com/softility/omivertex/service/AiExecutorTest.java
git commit -m "feat: AiExecutor bulkhead — bounded pool, fast 503 on saturation

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 2: Async AI endpoints + MockMvc async pattern

**Files:**
- Modify: `src/test/java/com/softility/omivertex/api/ApiTestBase.java` (async helper)
- Modify: `src/test/java/com/softility/omivertex/api/AssistantApiTest.java`
- Modify: `src/test/java/com/softility/omivertex/api/ResumeApiTest.java`
- Modify: `src/main/java/com/softility/omivertex/web/AssistantController.java`
- Modify: `src/main/java/com/softility/omivertex/web/ResumeController.java`
- Modify: `src/main/java/com/softility/omivertex/web/MeController.java`

- [ ] **Step 1: Async helper in `ApiTestBase`**

```java
/** Two-step MockMvc for async (CompletableFuture) endpoints. */
protected org.springframework.test.web.servlet.ResultActions asyncPerform(
        org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
    org.springframework.test.web.servlet.MvcResult started = mockMvc.perform(request)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                    .request().asyncStarted())
            .andReturn();
    return mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch(started));
}
```

(Use plain imports at the top of the file; shown qualified here for unambiguity.)

- [ ] **Step 2: Flip the affected tests to `asyncPerform` (red)**

- `AssistantApiTest`: every `mockMvc.perform(post("/api/v1/assistant/chat")...)` whose request reaches the controller → `asyncPerform(...)`. Exceptions that stay on plain `mockMvc.perform`: the two `chat_blankOrOversizedMessage_returns400` calls (bean validation rejects before the controller body runs) and the ASSOCIATE-forbidden call in `chat_viewerAllowed_associateForbidden` (security filter rejects pre-controller). The viewer-allowed call in that test goes async.
- `ResumeApiTest`: all `multipart("/api/v1/resumes/parse")` and `multipart("/api/v1/me/resumes/parse")` performs → `asyncPerform(...)`. Upload/download/delete endpoints stay sync.

Run: `./mvnw test -Dtest='AssistantApiTest,ResumeApiTest'`
Expected: FAIL — `asyncStarted()` assertion fails because the endpoints are still synchronous. That is the red state.

- [ ] **Step 3: Make the endpoints async (green)**

`AssistantController` — inject `AiExecutor aiExecutor` via constructor and change:

```java
@PostMapping("/chat")
public java.util.concurrent.CompletableFuture<AssistantChatResponse> chat(
        @Valid @RequestBody AssistantChatRequest request) {
    return aiExecutor.submit(() -> assistantService.chat(request));
}
```

`ResumeController` — inject `AiExecutor aiExecutor` and change:

```java
@PostMapping("/resumes/parse")
public java.util.concurrent.CompletableFuture<ParsedResumeResponse> parseResume(
        @RequestParam("file") MultipartFile file) {
    return aiExecutor.submit(() -> resumeService.parse(file));
}
```

`MeController` — inject `AiExecutor aiExecutor` and change:

```java
@PostMapping("/resumes/parse")
public java.util.concurrent.CompletableFuture<ParsedResumeResponse> parseResume(
        @RequestParam("file") MultipartFile file) {
    return aiExecutor.submit(() -> resumeService.parse(file));
}
```

(Plain imports at the top of each file, matching house style.)

- [ ] **Step 4: Full suite green + commit**

Run: `./mvnw test`
Expected: BUILD SUCCESS, all tests green (Spotless: `./mvnw spotless:apply` if flagged).

```bash
git add -A src/main src/test
git commit -m "feat: AI endpoints go async on the bulkhead — servlet threads freed during Gemini calls

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 3: Gemini HTTP timeouts

**Files:**
- Modify: `src/test/java/com/softility/omivertex/service/GeminiHttpClientTest.java` (constructor gains timeout params)
- Modify: `src/main/java/com/softility/omivertex/service/GeminiHttpClient.java`
- Modify: `src/main/resources/application.properties`

- [ ] **Step 1: Update the unit test constructor calls (red — constructor mismatch)**

In `GeminiHttpClientTest`, every `new GeminiHttpClient("", "gemini-2.5-flash")` becomes:

```java
new GeminiHttpClient("", "gemini-2.5-flash",
        java.time.Duration.ofSeconds(5), java.time.Duration.ofSeconds(30))
```

Run: `./mvnw test-compile -q` → compile failure = red.

- [ ] **Step 2: Implement**

In `GeminiHttpClient`, replace the field/constructor block:

```java
private final String apiKey;
private final String model;
private final RestClient rest;

public GeminiHttpClient(
        @Value("${omivertex.assistant.gemini.api-key:}") String apiKey,
        @Value("${omivertex.assistant.gemini.model:gemini-3.1-flash-lite}") String model,
        @Value("${omivertex.assistant.gemini.connect-timeout:5s}") Duration connectTimeout,
        @Value("${omivertex.assistant.gemini.read-timeout:30s}") Duration readTimeout) {
    this.apiKey = apiKey == null ? "" : apiKey.trim();
    this.model = model;
    // Bounded I/O: a hung upstream call must never hold an ai-* thread forever.
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout((int) connectTimeout.toMillis());
    requestFactory.setReadTimeout((int) readTimeout.toMillis());
    this.rest = RestClient.builder().requestFactory(requestFactory).build();
    if (this.apiKey.isEmpty()) {
        log.warn("omivertex.assistant.gemini.api-key is not set — the AI assistant is disabled "
                + "(the endpoint will return 400). Set it to enable the dashboard assistant.");
    }
}
```

Imports: `java.time.Duration`, `org.springframework.http.client.SimpleClientHttpRequestFactory`.
(Timeouts become `ResourceAccessException` → already caught by the generic catch → "unavailable right now" 400. If `@Value` cannot convert `5s` to `Duration` in this Boot version, the app context fails fast at startup — the full-suite run in Step 3 catches it; fallback is `String` params + `DurationStyle.detectAndParse`.)

In `application.properties`, under the existing Gemini block:

```properties
omivertex.assistant.gemini.connect-timeout=${OMIVERTEX_ASSISTANT_GEMINI_CONNECT_TIMEOUT:5s}
omivertex.assistant.gemini.read-timeout=${OMIVERTEX_ASSISTANT_GEMINI_READ_TIMEOUT:30s}
```

- [ ] **Step 3: Full suite green + commit**

Run: `./mvnw test` → BUILD SUCCESS (context startup exercises the `Duration` binding).

```bash
git add src/main/java/com/softility/omivertex/service/GeminiHttpClient.java \
        src/test/java/com/softility/omivertex/service/GeminiHttpClientTest.java \
        src/main/resources/application.properties
git commit -m "feat: connect/read timeouts on Gemini HTTP client (5s/30s, configurable)

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 4: Docs + AGENTS.md plans convention + wrap-up

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/TECHNICAL.md`
- Modify: `docs/TODO.md`

- [ ] **Step 1: AGENTS.md** — in the "How to work here" rule 0 (spec → plan → TDD), append:

```markdown
   Plan docs are **write-once scaffolding** for a single implementation run —
   never update an old plan. Specs and `docs/TECHNICAL.md` are the living
   documentation; plans stay as historical record only.
```

- [ ] **Step 2: TECHNICAL.md** — error contract section gains the 503 row (`ServiceUnavailableException` → 503, "AI executor saturated — retry"); note on the three AI endpoints that they execute on a dedicated 4-thread pool with 5s/30s timeouts and identical response contracts.

`docs/TODO.md` "Resolved decisions":

```markdown
- **AI bulkhead + timeouts** (2026-07-11): all Gemini calls run on a dedicated
  4-thread executor (queue 8; saturation → fast 503) behind async controllers,
  with 5s connect / 30s read timeouts. Sized small deliberately — the upstream
  is the bottleneck; revisit pool size only with observed contention. This is
  the "async before more AI features" decision, chosen over a job queue.
```

- [ ] **Step 3: Full suite + frontend untouched check + graph + commit**

Run: `./mvnw test` → green. No frontend files changed (contract identical) — no npm build needed.
Run: `$(cat graphify-out/.graphify_python) -c "from graphify.watch import _rebuild_code; from pathlib import Path; _rebuild_code(Path('.'))"`

```bash
git add AGENTS.md docs/TECHNICAL.md docs/TODO.md
git commit -m "docs: AI bulkhead contract, plans-are-disposable convention

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```
