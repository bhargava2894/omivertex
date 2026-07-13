package com.softility.omivertex.api;

import com.softility.omivertex.domain.WorkMode;
import com.softility.omivertex.service.GeminiClient;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
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
        when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any()))
                .thenReturn(new GeminiClient.AssistantReply("Priya Sharma is on the bench.", null));

        asyncPerform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"who is on the bench?","history":[]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Priya Sharma is on the bench."));

        // the live roster went along as context
        ArgumentCaptor<String> context = ArgumentCaptor.forClass(String.class);
        verify(geminiClient).replyWithTools(context.capture(), anyList(), anyString(), any());
        // minimal-context design: the standing context carries aggregates, never roster rows
        assertThat(context.getValue()).doesNotContain("Priya Sharma");
        assertThat(context.getValue()).contains("Active associates: 1");
    }

    @Test
    void chat_viewerAllowed_associateForbidden() throws Exception {
        when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any()))
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
        when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any()))
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
        verify(geminiClient).replyWithTools(any(), turns.capture(), any(), any());
        assertThat(turns.getValue()).hasSize(20);
        assertThat(turns.getValue().get(0).content()).isEqualTo("turn 10"); // oldest dropped
    }

    @Test
    void chat_draftsAllocation_fromToolCall() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var priya = associate("Priya Sharma", "priya@softility.com", WorkMode.ONSHORE);
        when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any()))
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
        when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any()))
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
        when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any()))
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
        when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any()))
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
        when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any()))
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
    void chat_readTool_projectDetail_listsRoster() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var asha = associate("Asha Nair", "asha@softility.com", WorkMode.ONSHORE);
        allocation(asha, proj, true);
        when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any()))
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
        when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any()))
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
        when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any()))
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
}
