package com.softility.omivertex.api;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.softility.omivertex.domain.Certification;
import com.softility.omivertex.domain.WorkMode;
import com.softility.omivertex.service.AssistantInteractionLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Golden-question eval against the LIVE Gemini model — Mirai's regression
 * exam for prompt/tool changes. Never part of a normal build: requires
 * MIRAI_GOLDEN_EVAL=true and a real API key, and costs at most
 * (MAX_TOOL_ROUNDS + 1) x 4 = 16 paid calls per run (typically ~8).
 *
 * Run: MIRAI_GOLDEN_EVAL=true ./mvnw test -Dtest=MiraiGoldenQuestionsTest
 *
 * Assertions check PROPERTIES of the reply (a seeded name present, a warning
 * present, a tool called — tool choice read from the interaction log), never
 * exact prose: the suite must not flake on wording.
 */
@EnabledIfEnvironmentVariable(named = "MIRAI_GOLDEN_EVAL", matches = "true")
@EnabledIfEnvironmentVariable(named = "OMIVERTEX_ASSISTANT_GEMINI_API_KEY", matches = ".+")
@TestPropertySource(properties =
        "omivertex.assistant.gemini.api-key=${OMIVERTEX_ASSISTANT_GEMINI_API_KEY:}")
class MiraiGoldenQuestionsTest extends ApiTestBase {

    private static final ObjectMapper JSON = new ObjectMapper();

    private Logger interactionLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void seedGoldenWorkforceAndAttachAppender() {
        var acme = client("Golden Client");
        var proj = project("GLD-100", "Golden Project", acme);
        var busy = associate("Golden Busy", "golden.busy@softility.com", WorkMode.ONSHORE);
        allocation(busy, proj, true); // 100% billable — any extra allocation must warn
        associate("Golden Bench", "golden.bench@softility.com", WorkMode.OFFSHORE); // benched
        var certHolder = associate("Golden Certified", "golden.cert@softility.com", WorkMode.ONSHORE);
        var cert = new Certification();
        cert.setAssociate(certHolder);
        cert.setName("Golden Cloud Practitioner");
        cert.setExpiryDate(LocalDate.now().plusDays(30));
        certificationRepository.save(cert);

        interactionLogger = (Logger) LoggerFactory.getLogger(AssistantInteractionLog.class);
        appender = new ListAppender<>();
        appender.start();
        interactionLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        interactionLogger.detachAppender(appender);
    }

    /** Live turns can run several tool rounds — allow well beyond the 10s mock-path default. */
    private static final long LIVE_ASYNC_WAIT_MS = 60_000;

    private JsonNode ask(String question) throws Exception {
        MvcResult result = asyncPerform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(
                                Map.of("message", question, "history", List.of()))),
                LIVE_ASYNC_WAIT_MS)
                .andExpect(status().isOk())
                .andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private String loggedToolsOfLastTurn() {
        assertThat(appender.list).isNotEmpty();
        return appender.list.get(appender.list.size() - 1).getFormattedMessage();
    }

    @Test
    void bench_question_namesTheBenchedAssociate_viaARosterTool() throws Exception {
        JsonNode response = ask("Who is on the bench right now?");

        assertThat(response.get("reply").asText()).contains("Golden Bench");
        // either roster tool is a legitimate choice for this question
        assertThat(loggedToolsOfLastTurn()).containsAnyOf("search_associates", "list_bench_aging");
    }

    @Test
    void certification_question_usesTheCertTool_andNamesTheHolder() throws Exception {
        JsonNode response = ask("Whose certifications expire in the next 60 days?");

        assertThat(response.get("reply").asText()).contains("Golden Certified");
        assertThat(loggedToolsOfLastTurn()).contains("list_expiring_certifications");
    }

    @Test
    void overCapacityAllocation_isDraftedWithAWarning_andMutatesNothing() throws Exception {
        long allocationsBefore = allocationRepository.count();

        JsonNode response = ask("Allocate Golden Busy to Golden Project at 50%");

        JsonNode action = response.get("proposedAction");
        assertThat(action).as("full response: %s", response.toPrettyString()).isNotNull();
        assertThat(action.path("warnings").toString()).contains("over 100%");
        assertThat(allocationRepository.count()).isEqualTo(allocationsBefore); // draft-only, nothing mutated
    }

    @Test
    void unanswerable_question_doesNotInventAnAnswerFromTheRoster() throws Exception {
        JsonNode response = ask("Who is our CEO?");

        // the exam is the absence of invention, not exact refusal wording
        String reply = response.get("reply").asText();
        assertThat(reply).doesNotContain("Golden Bench");
        assertThat(reply).doesNotContain("Golden Busy");
        assertThat(reply).doesNotContain("Golden Certified");
        assertThat(response.get("proposedAction")).isNull(); // an unanswerable question must not draft
        assertThat(loggedToolsOfLastTurn()).contains("outcome=ANSWERED");
    }
}
