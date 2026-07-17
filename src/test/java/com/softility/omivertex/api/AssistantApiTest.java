package com.softility.omivertex.api;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.softility.omivertex.domain.WorkMode;
import com.softility.omivertex.service.AssistantInteractionLog;
import com.softility.omivertex.service.GeminiClient;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AssistantApiTest extends ApiTestBase {

    @MockBean GeminiClient geminiClient;

    @Test
    void chat_answersWithWorkforceContext() throws Exception {
        associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE); // on bench
        when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any(), anyBoolean()))
                .thenReturn(new GeminiClient.AssistantReply("Priya Sharma is on the bench.", null));

        asyncPerform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"who is on the bench?","history":[]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Priya Sharma is on the bench."));

        // the live roster went along as context
        ArgumentCaptor<String> context = ArgumentCaptor.forClass(String.class);
        verify(geminiClient).replyWithTools(context.capture(), anyList(), anyString(), any(), anyBoolean());
        // minimal-context design: the standing context carries aggregates, never roster rows
        assertThat(context.getValue()).doesNotContain("Priya Sharma");
        assertThat(context.getValue()).contains("Active associates: 1");
    }

    @Test
    void chat_viewerAllowed_associateForbidden() throws Exception {
        when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any(), anyBoolean()))
                .thenReturn(new GeminiClient.AssistantReply("ok", null));
        asyncPerform(post("/api/v1/assistant/chat")
                        .with(SecurityMockMvcRequestPostProcessors.user("viewer").roles("VIEWER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"hello","history":[]}"""))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/assistant/chat")
                        .with(SecurityMockMvcRequestPostProcessors.user("a@softility.com").roles("ASSOCIATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"hello","history":[]}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void chat_blankOrOversizedMessage_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"","history":[]}"""))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"%s","history":[]}""".formatted("x".repeat(2001))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @SuppressWarnings("unchecked")
    void chat_capsHistoryToLast20Turns() throws Exception {
        when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any(), anyBoolean()))
                .thenReturn(new GeminiClient.AssistantReply("ok", null));
        StringBuilder history = new StringBuilder("[");
        for (int i = 0; i < 30; i++) {
            if (i > 0) {
                history.append(",");
            }
            history.append("""
                    {"role":"user","content":"turn %d"}""".formatted(i));
        }
        history.append("]");
        asyncPerform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"hello","history":%s}""".formatted(history)))
                .andExpect(status().isOk());

        ArgumentCaptor<List<GeminiClient.Turn>> turns = ArgumentCaptor.forClass(List.class);
        verify(geminiClient).replyWithTools(any(), turns.capture(), any(), any(), anyBoolean());
        assertThat(turns.getValue()).hasSize(20);
        assertThat(turns.getValue().get(0).content()).isEqualTo("turn 10"); // oldest dropped
    }

    @Test
    void chat_draftsAllocation_fromToolCall() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var priya = associate("Priya Sharma", "priya@softility.com", WorkMode.ONSHORE);
        when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any(), anyBoolean()))
                .thenReturn(new GeminiClient.AssistantReply("",
                        new GeminiClient.ActionCall("propose_allocation",
                                Map.of("associateName", "Priya Sharma", "projectName", "Storefront Revamp",
                                        "percent", 50, "billable", true))));

        asyncPerform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"allocate priya to storefront at 50%","history":[]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proposedAction.type").value("CREATE_ALLOCATION"))
                .andExpect(jsonPath("$.proposedAction.associateId").value(priya.getId()))
                .andExpect(jsonPath("$.proposedAction.projectId").value(proj.getId()))
                .andExpect(jsonPath("$.proposedAction.percent").value(50))
                .andExpect(jsonPath("$.proposedAction.billable").value(true))
                .andExpect(jsonPath("$.proposedAction.warnings").isEmpty())
                .andExpect(jsonPath("$.reply").isNotEmpty());
    }

    @Test
    void chat_ambiguousAssociate_asksInsteadOfDrafting() throws Exception {
        var acme = client("Acme Corp");
        project("ACM-100", "Storefront Revamp", acme);
        associate("Priya Sharma", "priya@softility.com", WorkMode.ONSHORE);
        associate("Priya Verma", "priya.v@softility.com", WorkMode.ONSHORE);
        when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any(), anyBoolean()))
                .thenReturn(new GeminiClient.AssistantReply("",
                        new GeminiClient.ActionCall("propose_allocation",
                                Map.of("associateName", "Priya", "projectName", "Storefront Revamp"))));

        asyncPerform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"allocate priya to storefront","history":[]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proposedAction").doesNotExist())
                .andExpect(jsonPath("$.reply", containsString("Priya Sharma")))
                .andExpect(jsonPath("$.reply", containsString("Priya Verma")));
    }

    @Test
    void chat_overCapacityDraft_carriesWarning() throws Exception {
        var acme = client("Acme Corp");
        project("ACM-100", "Storefront Revamp", acme);
        var other = project("ACM-200", "Data Platform", acme);
        var priya = associate("Priya Sharma", "priya@softility.com", WorkMode.ONSHORE);
        allocation(priya, other, true);
        when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any(), anyBoolean()))
                .thenReturn(new GeminiClient.AssistantReply("",
                        new GeminiClient.ActionCall("propose_allocation",
                                Map.of("associateName", "Priya Sharma", "projectName", "Storefront Revamp",
                                        "percent", 50))));

        asyncPerform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"add priya to storefront at 50%","history":[]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proposedAction.warnings[0]", containsString("100%")));
    }

    @Test
    void chat_draftsPositionFill() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var pos = new com.softility.omivertex.domain.OpenPosition();
        pos.setTitle("Java Dev");
        pos.setProject(proj);
        openPositionRepository.save(pos);
        var priya = associate("Priya Sharma", "priya@softility.com", WorkMode.ONSHORE);
        when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any(), anyBoolean()))
                .thenReturn(new GeminiClient.AssistantReply("",
                        new GeminiClient.ActionCall("propose_position_fill",
                                Map.of("positionTitle", "Java Dev", "associateName", "Priya Sharma"))));

        asyncPerform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"fill the java dev seat with priya","history":[]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proposedAction.type").value("FILL_POSITION"))
                .andExpect(jsonPath("$.proposedAction.positionId").value(pos.getId()))
                .andExpect(jsonPath("$.proposedAction.associateId").value(priya.getId()));
    }

    @Test
    void chat_readTool_executesPositionMatches() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var pos = new com.softility.omivertex.domain.OpenPosition();
        pos.setTitle("Java Dev");
        pos.setProject(proj);
        openPositionRepository.save(pos);
        associate("Priya Sharma", "priya@softility.com", WorkMode.ONSHORE); // on bench
        when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any(), anyBoolean()))
                .thenAnswer(inv -> {
                    GeminiClient.ToolExecutor ex = inv.getArgument(3);
                    String result = ex.execute("get_position_matches", Map.of("positionTitle", "Java Dev"));
                    return new GeminiClient.AssistantReply("Matches: " + result, null);
                });

        asyncPerform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"who matches the java dev seat?","history":[]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply", containsString("Priya Sharma")));
    }

    @Test
    void chat_readTool_searchAssociates() throws Exception {
        var java = skill("Backend", "Java");
        var priya = associate("Priya Sharma", "priya@softility.com", WorkMode.ONSHORE);
        rateSkill(priya, java, com.softility.omivertex.domain.Proficiency.ADVANCE);
        when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any(), anyBoolean()))
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
    void chat_readTool_listClients_namesClientsWithNoOpenPositions() throws Exception {
        client("Acme Corp");
        client("Quiet Holdings"); // reachable through no other read tool
        when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any(), anyBoolean()))
                .thenAnswer(inv -> {
                    GeminiClient.ToolExecutor ex = inv.getArgument(3);
                    return new GeminiClient.AssistantReply(
                            ex.execute("list_clients", Map.of()), null);
                });

        asyncPerform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"who are our clients?","history":[]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply", containsString("Quiet Holdings")))
                .andExpect(jsonPath("$.reply", containsString("Acme Corp")));
    }

    @Test
    void chat_readTool_listProjects_filtersByClient() throws Exception {
        var acme = client("Acme Corp");
        project("ACM-100", "Storefront Revamp", acme);
        var helios = client("Helios Energy");
        project("HEL-100", "Grid Analytics", helios);
        when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any(), anyBoolean()))
                .thenAnswer(inv -> {
                    GeminiClient.ToolExecutor ex = inv.getArgument(3);
                    return new GeminiClient.AssistantReply(
                            ex.execute("list_projects", Map.of("clientName", "Helios")), null);
                });

        asyncPerform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"what is helios running?","history":[]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply", containsString("Grid Analytics")))
                .andExpect(jsonPath("$.reply", not(containsString("Storefront Revamp"))));
    }

    @Test
    void chat_readTool_projectDetail_listsRoster() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var asha = associate("Asha Nair", "asha@softility.com", WorkMode.ONSHORE);
        allocation(asha, proj, true);
        when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any(), anyBoolean()))
                .thenAnswer(inv -> {
                    GeminiClient.ToolExecutor ex = inv.getArgument(3);
                    return new GeminiClient.AssistantReply(
                            ex.execute("get_project_detail", Map.of("projectName", "Storefront Revamp")), null);
                });

        asyncPerform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"who is on storefront revamp?","history":[]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply", containsString("Asha Nair")));
    }

    @Test
    void chat_readTool_associateDetail_findsFormerEmployee() throws Exception {
        var alum = associate("Nikhil Rao", "nikhil@softility.com", WorkMode.OFFSHORE);
        alum.setStatus(com.softility.omivertex.domain.EntityStatus.INACTIVE);
        alum.setLastWorkingDay(java.time.LocalDate.now().minusMonths(1));
        alum.setExitReason(com.softility.omivertex.domain.ExitReason.RESIGNED);
        associateRepository.save(alum);
        when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any(), anyBoolean()))
                .thenAnswer(inv -> {
                    GeminiClient.ToolExecutor ex = inv.getArgument(3);
                    return new GeminiClient.AssistantReply(
                            ex.execute("get_associate_detail", Map.of("name", "Nikhil Rao")), null);
                });

        asyncPerform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"tell me about nikhil","history":[]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply", containsString("FORMER EMPLOYEE")));
    }

    @Test
    void chat_draftAllocation_refusesFormerEmployee() throws Exception {
        var acme = client("Acme Corp");
        project("ACM-100", "Storefront Revamp", acme);
        var alum = associate("Nikhil Rao", "nikhil@softility.com", WorkMode.OFFSHORE);
        alum.setStatus(com.softility.omivertex.domain.EntityStatus.INACTIVE);
        associateRepository.save(alum);
        when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any(), anyBoolean()))
                .thenReturn(new GeminiClient.AssistantReply("",
                        new GeminiClient.ActionCall("propose_allocation",
                                Map.of("associateName", "Nikhil Rao", "projectName", "Storefront Revamp"))));

        asyncPerform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"allocate nikhil to storefront","history":[]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proposedAction").doesNotExist())
                .andExpect(jsonPath("$.reply", containsString("couldn't find an active associate")));
    }

    @Test
    void chat_readTool_associateDetail_ambiguousNameAsksBack() throws Exception {
        associate("Priya Sharma", "priya@softility.com", WorkMode.ONSHORE);
        associate("Priya Verma", "priya.v@softility.com", WorkMode.ONSHORE);
        when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any(), anyBoolean()))
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

        when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any(), anyBoolean()))
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

    @Test
    void chat_dispatchesExpiringCertificationsTool_defaultsTo90Days() throws Exception {
        var holder = associate("Ravi Kumar", "ravi@softility.com", WorkMode.OFFSHORE);
        var cert = new com.softility.omivertex.domain.Certification();
        cert.setAssociate(holder);
        cert.setName("AWS Solutions Architect");
        cert.setExpiryDate(java.time.LocalDate.now().plusDays(80)); // inside 90, outside 30
        certificationRepository.save(cert);

        when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any(), anyBoolean()))
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

    @Test
    void chat_dispatchesWorkforceSummaryTool() throws Exception {
        associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE); // 1 active, benched

        when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any(), anyBoolean()))
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

    @Test
    void chat_dispatchesBenchAgingTool() throws Exception {
        associate("Rahul Verma", "rahul@softility.com", WorkMode.ONSHORE); // benched

        when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any(), anyBoolean()))
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

    @Test
    void chat_dispatchesPositionMatchSummaryTool() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var position = new com.softility.omivertex.domain.OpenPosition();
        position.setTitle("Java Dev");
        position.setProject(proj);
        openPositionRepository.save(position);

        when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any(), anyBoolean()))
                .thenAnswer(inv -> {
                    GeminiClient.ToolExecutor ex = inv.getArgument(3);
                    return new GeminiClient.AssistantReply(
                            ex.execute("get_position_match_summary", Map.of()), null);
                });

        asyncPerform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"which open positions have no bench match?","history":[]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply", containsString("Java Dev")));
    }

    private ListAppender<ILoggingEvent> attachInteractionAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(AssistantInteractionLog.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private void detachInteractionAppender(ListAppender<ILoggingEvent> appender) {
        ((Logger) LoggerFactory.getLogger(AssistantInteractionLog.class)).detachAppender(appender);
    }

    @Test
    void chat_logsAnsweredTurnWithUserToolsAndQuestion_neverTheReply() throws Exception {
        associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE); // benched
        when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any(), anyBoolean()))
                .thenAnswer(inv -> {
                    GeminiClient.ToolExecutor ex = inv.getArgument(3);
                    ex.execute("search_associates", Map.of("benchOnly", true));
                    return new GeminiClient.AssistantReply("Priya Sharma is on the bench.", null);
                });

        ListAppender<ILoggingEvent> appender = attachInteractionAppender();
        try {
            asyncPerform(post("/api/v1/assistant/chat")
                            .with(SecurityMockMvcRequestPostProcessors.user("viewer@softility.com").roles("VIEWER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"message":"who is on the bench?","history":[]}"""))
                    .andExpect(status().isOk());

            assertThat(appender.list).hasSize(1);
            String line = appender.list.get(0).getFormattedMessage();
            assertThat(line).contains("MIRAI user=viewer@softility.com");
            assertThat(line).contains("outcome=ANSWERED");
            assertThat(line).contains("tools=[search_associates]");
            assertThat(line).contains("question=\"who is on the bench?\"");
            assertThat(line).contains("latencyMs=");
            // privacy pin: the reply text is never logged
            assertThat(line).doesNotContain("Priya Sharma is on the bench.");
        } finally {
            detachInteractionAppender(appender);
        }
    }

    @Test
    void chat_logsDraftedWhenAProposedActionIsReturned() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any(), anyBoolean()))
                .thenReturn(new GeminiClient.AssistantReply("Here you go.",
                        new GeminiClient.ActionCall("propose_allocation",
                                Map.of("associateName", "Priya Sharma", "projectName", "Storefront Revamp"))));

        ListAppender<ILoggingEvent> appender = attachInteractionAppender();
        try {
            asyncPerform(post("/api/v1/assistant/chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"message":"allocate priya to storefront","history":[]}"""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.proposedAction.type").value("CREATE_ALLOCATION"));

            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.get(0).getFormattedMessage()).contains("outcome=DRAFTED");
        } finally {
            detachInteractionAppender(appender);
        }
    }

    @Test
    void chat_logsErrorWhenTheTurnThrows_andTheErrorStillReachesTheClient() throws Exception {
        when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any(), anyBoolean()))
                .thenThrow(new com.softility.omivertex.web.error.BadRequestException(
                        "The AI assistant is unavailable right now — try again shortly"));

        ListAppender<ILoggingEvent> appender = attachInteractionAppender();
        try {
            asyncPerform(post("/api/v1/assistant/chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"message":"hello","history":[]}"""))
                    .andExpect(status().isBadRequest());

            assertThat(appender.list).hasSize(1);
            String line = appender.list.get(0).getFormattedMessage();
            assertThat(line).contains("outcome=ERROR");
            assertThat(line).contains("question=\"hello\"");
        } finally {
            detachInteractionAppender(appender);
        }
    }

    @Test
    void chat_adminToolWorksForAdmin_andIsUnknownForViewer() throws Exception {
        when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any(), anyBoolean()))
                .thenAnswer(inv -> {
                    GeminiClient.ToolExecutor ex = inv.getArgument(3);
                    return new GeminiClient.AssistantReply(
                            ex.execute("list_pending_approvals", Map.of()), null);
                });

        asyncPerform(post("/api/v1/assistant/chat")
                        .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"what needs my attention?","history":[]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply", containsString("Nothing is waiting for approval")));

        asyncPerform(post("/api/v1/assistant/chat")
                        .with(SecurityMockMvcRequestPostProcessors.user("viewer").roles("VIEWER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"what needs my attention?","history":[]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply", containsString("Unknown tool: list_pending_approvals")));
    }

    @Test
    void chat_auditHistoryToolIsAdminOnly() throws Exception {
        var e = new com.softility.omivertex.domain.AuditEntry();
        e.setUsername("admin");
        e.setAction("CREATE");
        e.setEntityType("Client");
        e.setEntityId(7L);
        e.setSummary("created Acme");
        auditEntryRepository.save(e);
        when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any(), anyBoolean()))
                .thenAnswer(inv -> {
                    GeminiClient.ToolExecutor ex = inv.getArgument(3);
                    return new GeminiClient.AssistantReply(
                            ex.execute("get_audit_history", Map.of("entityType", "Client")), null);
                });

        asyncPerform(post("/api/v1/assistant/chat")
                        .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"who created acme?","history":[]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply", containsString("created Acme")));
    }

    @Test
    void chat_draftsEndAllocation_withResolvedAllocationAndDefaultToday() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var priya = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        var alloc = allocation(priya, proj, true);
        when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any(), anyBoolean()))
                .thenReturn(new GeminiClient.AssistantReply("Draft ready.",
                        new GeminiClient.ActionCall("propose_end_allocation",
                                Map.of("associateName", "Priya Sharma",
                                        "projectName", "Storefront Revamp"))));

        asyncPerform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"roll priya off storefront","history":[]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proposedAction.type").value("END_ALLOCATION"))
                .andExpect(jsonPath("$.proposedAction.allocationId").value(alloc.getId().intValue()))
                .andExpect(jsonPath("$.proposedAction.endDate").value(java.time.LocalDate.now().toString()))
                .andExpect(jsonPath("$.proposedAction.summary",
                        containsString("End Priya Sharma's allocation on Storefront Revamp")));
    }

    @Test
    void chat_endAllocation_noCurrentAllocation_asksBack() throws Exception {
        var acme = client("Acme Corp");
        project("ACM-100", "Storefront Revamp", acme);
        associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE); // benched
        when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any(), anyBoolean()))
                .thenReturn(new GeminiClient.AssistantReply("",
                        new GeminiClient.ActionCall("propose_end_allocation",
                                Map.of("associateName", "Priya Sharma",
                                        "projectName", "Storefront Revamp"))));

        asyncPerform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"roll priya off storefront","history":[]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proposedAction").doesNotExist())
                .andExpect(jsonPath("$.reply",
                        containsString("no current allocation on Storefront Revamp")));
    }

    @Test
    void chat_endAllocation_endBeforeStart_isRejectedAtDraftTime() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var priya = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        allocation(priya, proj, true); // started 3 months ago
        when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any(), anyBoolean()))
                .thenReturn(new GeminiClient.AssistantReply("",
                        new GeminiClient.ActionCall("propose_end_allocation",
                                Map.of("associateName", "Priya Sharma",
                                        "projectName", "Storefront Revamp",
                                        "endDate", java.time.LocalDate.now().minusMonths(6).toString()))));

        asyncPerform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"end it half a year ago","history":[]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proposedAction").doesNotExist())
                .andExpect(jsonPath("$.reply", containsString("can't be before")));
    }

    @Test
    void chat_draftsEditAllocation_mergingOverCurrentValues() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var priya = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        var alloc = allocation(priya, proj, true); // 100%, billable, no end date
        alloc.setAllocationPercent(50);
        allocationRepository.save(alloc);
        when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any(), anyBoolean()))
                .thenReturn(new GeminiClient.AssistantReply("",
                        new GeminiClient.ActionCall("propose_edit_allocation",
                                Map.of("associateName", "Priya Sharma",
                                        "projectName", "Storefront Revamp",
                                        "percent", 80))));

        asyncPerform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"bump priya to 80%","history":[]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proposedAction.type").value("EDIT_ALLOCATION"))
                .andExpect(jsonPath("$.proposedAction.percent").value(80))
                .andExpect(jsonPath("$.proposedAction.billable").value(true)) // kept from current
                // raising 50 -> 80 must NOT warn against her own 50
                .andExpect(jsonPath("$.proposedAction.warnings").isEmpty());
    }

    @Test
    void chat_editAllocation_capacityWarningCountsOnlyOtherAllocations() throws Exception {
        var acme = client("Acme Corp");
        var p1 = project("ACM-100", "Storefront Revamp", acme);
        var p2 = project("ACM-200", "Data Platform", acme);
        var priya = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        var a1 = allocation(priya, p1, true);
        a1.setAllocationPercent(50);
        allocationRepository.save(a1);
        var a2 = allocation(priya, p2, true);
        a2.setAllocationPercent(40);
        allocationRepository.save(a2);
        when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any(), anyBoolean()))
                .thenReturn(new GeminiClient.AssistantReply("",
                        new GeminiClient.ActionCall("propose_edit_allocation",
                                Map.of("associateName", "Priya Sharma",
                                        "projectName", "Storefront Revamp",
                                        "percent", 70))));

        asyncPerform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"bump priya to 70% on storefront","history":[]}"""))
                .andExpect(status().isOk())
                // 70 + the OTHER 40 = 110 -> warn
                .andExpect(jsonPath("$.proposedAction.warnings[0]", containsString("over 100%")));
    }

    @Test
    void chat_draftsPosition_withResolvedSkillAndDefaults() throws Exception {
        var acme = client("Acme Corp");
        project("ACM-100", "Storefront Revamp", acme);
        var react = skill("Frontend", "React");
        when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any(), anyBoolean()))
                .thenReturn(new GeminiClient.AssistantReply("",
                        new GeminiClient.ActionCall("propose_position",
                                Map.of("title", "Senior React Developer",
                                        "projectName", "Storefront Revamp",
                                        "skillName", "react",
                                        "minProficiency", "ADVANCE"))));

        asyncPerform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"open a senior react position on storefront","history":[]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proposedAction.type").value("CREATE_POSITION"))
                .andExpect(jsonPath("$.proposedAction.positionTitle").value("Senior React Developer"))
                .andExpect(jsonPath("$.proposedAction.skillId").value(react.getId().intValue()))
                .andExpect(jsonPath("$.proposedAction.skillName").value("React"))
                .andExpect(jsonPath("$.proposedAction.minProficiency").value("ADVANCE"))
                .andExpect(jsonPath("$.proposedAction.percent").value(100))
                .andExpect(jsonPath("$.proposedAction.billable").value(true));
    }

    @Test
    void chat_position_unknownSkill_asksBack() throws Exception {
        var acme = client("Acme Corp");
        project("ACM-100", "Storefront Revamp", acme);
        when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any(), anyBoolean()))
                .thenReturn(new GeminiClient.AssistantReply("",
                        new GeminiClient.ActionCall("propose_position",
                                Map.of("title", "Sorcerer", "projectName", "Storefront Revamp",
                                        "skillName", "Dark Arts"))));

        asyncPerform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"we need a sorcerer","history":[]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proposedAction").doesNotExist())
                .andExpect(jsonPath("$.reply", containsString("couldn't find a skill matching \"Dark Arts\"")));
    }
}
