package com.softility.omivertex.api;

import com.softility.omivertex.domain.WorkMode;
import com.softility.omivertex.service.GeminiClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
        when(geminiClient.reply(anyString(), anyList(), anyString()))
                .thenReturn("Priya Sharma is on the bench.");

        mockMvc.perform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"who is on the bench?","history":[]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Priya Sharma is on the bench."));

        // the live roster went along as context
        ArgumentCaptor<String> context = ArgumentCaptor.forClass(String.class);
        verify(geminiClient).reply(context.capture(), anyList(), anyString());
        assertThat(context.getValue()).contains("Priya Sharma");
    }

    @Test
    void chat_viewerAllowed_associateForbidden() throws Exception {
        when(geminiClient.reply(anyString(), anyList(), anyString())).thenReturn("ok");
        mockMvc.perform(post("/api/v1/assistant/chat")
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
        when(geminiClient.reply(anyString(), anyList(), anyString())).thenReturn("ok");
        StringBuilder history = new StringBuilder("[");
        for (int i = 0; i < 30; i++) {
            if (i > 0) {
                history.append(",");
            }
            history.append("""
                    {"role":"user","content":"turn %d"}""".formatted(i));
        }
        history.append("]");
        mockMvc.perform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"hello","history":%s}""".formatted(history)))
                .andExpect(status().isOk());

        ArgumentCaptor<List<GeminiClient.Turn>> turns = ArgumentCaptor.forClass(List.class);
        verify(geminiClient).reply(any(), turns.capture(), any());
        assertThat(turns.getValue()).hasSize(20);
        assertThat(turns.getValue().get(0).content()).isEqualTo("turn 10"); // oldest dropped
    }
}
