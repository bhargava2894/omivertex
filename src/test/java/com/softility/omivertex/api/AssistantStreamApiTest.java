package com.softility.omivertex.api;

import com.softility.omivertex.domain.WorkMode;
import com.softility.omivertex.service.GeminiClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The SSE variant of /assistant/chat: tool progress events, then the reply. */
class AssistantStreamApiTest extends ApiTestBase {

    @MockBean GeminiClient geminiClient;

    /**
     * The emitter completes on the ai-* thread — poll the mock response until the
     * terminal event AND its data line are both present (the event-name line is
     * written before the data line, so gating on the name alone races the writer).
     */
    private String awaitSse(MvcResult result) throws Exception {
        for (int i = 0; i < 200; i++) {
            String body = result.getResponse().getContentAsString();
            int terminal = Math.max(body.indexOf("event:reply"), body.indexOf("event:error"));
            if (terminal >= 0 && body.indexOf("data:", terminal) > terminal) {
                return body;
            }
            Thread.sleep(25);
        }
        return result.getResponse().getContentAsString();
    }

    private MvcResult startStream(String message) throws Exception {
        return mockMvc.perform(post("/api/v1/assistant/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + message + "\",\"history\":[]}"))
                .andExpect(request().asyncStarted())
                .andReturn();
    }

    @Test
    void stream_sendsToolEventsInOrder_thenTheReply() throws Exception {
        when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any()))
                .thenAnswer(inv -> {
                    GeminiClient.ToolExecutor ex = inv.getArgument(3);
                    ex.execute("list_open_positions", Map.of());
                    ex.execute("get_position_match_summary", Map.of());
                    return new GeminiClient.AssistantReply("Two positions lack matches.", null);
                });

        String body = awaitSse(startStream("which open positions have no bench match?"));

        assertThat(body).contains("event:tool");
        assertThat(body.indexOf("data:list_open_positions"))
                .isLessThan(body.indexOf("data:get_position_match_summary"));
        assertThat(body).contains("event:reply");
        assertThat(body).contains("Two positions lack matches.");
    }

    @Test
    void stream_replyEventCarriesProposedActionJson() throws Exception {
        var acme = client("Acme Corp");
        project("ACM-100", "Storefront Revamp", acme);
        associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any()))
                .thenReturn(new GeminiClient.AssistantReply("Here's the draft.",
                        new GeminiClient.ActionCall("propose_allocation",
                                Map.of("associateName", "Priya Sharma",
                                        "projectName", "Storefront Revamp"))));

        String body = awaitSse(startStream("allocate priya to storefront"));

        assertThat(body).contains("event:reply");
        assertThat(body).contains("CREATE_ALLOCATION"); // same JSON shape as the plain endpoint
    }

    @Test
    void stream_failureBecomesAnErrorEvent() throws Exception {
        when(geminiClient.replyWithTools(anyString(), anyList(), anyString(), any()))
                .thenThrow(new com.softility.omivertex.web.error.BadRequestException(
                        "The AI assistant is unavailable right now — try again shortly"));

        String body = awaitSse(startStream("hello"));

        assertThat(body).contains("event:error");
        assertThat(body).contains("unavailable right now");
    }

    @Test
    void stream_associateRoleIsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/assistant/chat/stream")
                        .with(SecurityMockMvcRequestPostProcessors.user("a@softility.com").roles("ASSOCIATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\",\"history\":[]}"))
                .andExpect(status().isForbidden());
    }
}
