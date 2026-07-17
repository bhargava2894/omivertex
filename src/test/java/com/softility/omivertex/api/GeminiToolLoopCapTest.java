package com.softility.omivertex.api;

import com.softility.omivertex.service.GeminiClient;
import com.softility.omivertex.service.GeminiHttpClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins MAX_TOOL_ROUNDS: even against a model that requests a tool on every
 * round forever, one assistant turn performs a bounded number of upstream
 * calls and returns prose. Without the cap a confused model means an infinite
 * paid-API loop; without the wrap-up call a read tool leaked out as an
 * ActionCall and the user got the misleading write-tool fallback.
 */
class GeminiToolLoopCapTest {

    private static final String ASK_FOR_A_TOOL = """
            {"candidates":[{"content":{"parts":[{"functionCall":{"name":"search_associates","args":{}}}]}}]}""";
    private static final String PROSE_ANSWER = """
            {"candidates":[{"content":{"parts":[{"text":"Partial answer from gathered data."}]}}]}""";

    private HttpServer server;

    /** Stub Gemini answering request #n (1-based) with {@code responseForCall.apply(n)}. */
    private GeminiHttpClient clientAgainstStub(AtomicInteger apiCalls,
                                               IntFunction<String> responseForCall) throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        // Registered at the exact expected path so the test also pins endpoint
        // construction — a malformed base-url/path join 404s and fails fast.
        server.createContext("/v1beta/models/test-model:generateContent", exchange -> {
            byte[] body = responseForCall.apply(apiCalls.incrementAndGet())
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        return new GeminiHttpClient("test-key", "test-model",
                "http://localhost:" + server.getAddress().getPort(),
                Duration.ofSeconds(2), Duration.ofSeconds(2));
    }

    @Test
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void capHit_wrapUpCallTurnsGatheredDataIntoProse() throws Exception {
        AtomicInteger apiCalls = new AtomicInteger();
        // The model asks for a tool on every round; only the wrap-up call yields prose.
        GeminiHttpClient client = clientAgainstStub(apiCalls,
                call -> call <= 4 ? ASK_FOR_A_TOOL : PROSE_ANSWER);
        AtomicInteger toolRuns = new AtomicInteger();

        GeminiClient.AssistantReply reply = client.replyWithTools("context", List.of(),
                "which open positions have no bench match?", (name, args) -> {
                    toolRuns.incrementAndGet();
                    return "rows";
                }, false);

        // MAX_TOOL_ROUNDS = 3: initial call + three tool rounds + one wrap-up call
        assertThat(apiCalls.get()).isEqualTo(5);
        assertThat(toolRuns.get()).isEqualTo(3);
        // the turn ends in prose from the gathered data — never a leaked read-tool ActionCall
        assertThat(reply.action()).isNull();
        assertThat(reply.text()).isEqualTo("Partial answer from gathered data.");
    }

    @Test
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void capHit_modelStillRefusesProse_fallsBackToNarrowingHint() throws Exception {
        AtomicInteger apiCalls = new AtomicInteger();
        GeminiHttpClient client = clientAgainstStub(apiCalls, call -> ASK_FOR_A_TOOL);

        GeminiClient.AssistantReply reply = client.replyWithTools("context", List.of(),
                "who is on the bench?", (name, args) -> "rows", false);

        // still strictly bounded: the wrap-up call is the last one, whatever it returns
        assertThat(apiCalls.get()).isEqualTo(5);
        assertThat(reply.action()).isNull();
        assertThat(reply.text()).contains("try narrowing");
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }
}
