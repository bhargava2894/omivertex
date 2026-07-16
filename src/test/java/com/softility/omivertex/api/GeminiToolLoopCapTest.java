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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins MAX_TOOL_ROUNDS: even against a model that requests a tool on every
 * round forever, one assistant turn performs a bounded number of upstream
 * calls and returns. Without this cap a confused model means an infinite
 * paid-API loop.
 */
class GeminiToolLoopCapTest {

    private HttpServer server;

    @Test
    @Timeout(10) // a regression here would otherwise hang the suite
    void replyWithTools_terminatesAfterMaxToolRounds_evenIfTheModelNeverStops() throws Exception {
        AtomicInteger apiCalls = new AtomicInteger();
        String alwaysAskForATool = """
                {"candidates":[{"content":{"parts":[{"functionCall":{"name":"search_associates","args":{}}}]}}]}""";
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            apiCalls.incrementAndGet();
            byte[] body = alwaysAskForATool.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();

        GeminiHttpClient client = new GeminiHttpClient("test-key", "test-model",
                "http://localhost:" + server.getAddress().getPort(),
                Duration.ofSeconds(2), Duration.ofSeconds(2));
        AtomicInteger toolRuns = new AtomicInteger();

        GeminiClient.AssistantReply reply = client.replyWithTools("context", List.of(),
                "who is on the bench?", (name, args) -> {
                    toolRuns.incrementAndGet();
                    return "rows";
                });

        // MAX_TOOL_ROUNDS = 3: the initial call plus three tool rounds, then a forced return
        assertThat(apiCalls.get()).isEqualTo(4);
        assertThat(toolRuns.get()).isEqualTo(3);
        // the uncompleted read-tool call surfaces as an ActionCall (documented fall-through:
        // AssistantService answers it with the polite "can't do that" fallback)
        assertThat(reply.action()).isNotNull();
        assertThat(reply.action().name()).isEqualTo("search_associates");
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }
}
