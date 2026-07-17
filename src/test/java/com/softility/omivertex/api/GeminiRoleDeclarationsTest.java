package com.softility.omivertex.api;

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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Pins role filtering at the wire: a viewer's request body never declares admin tools. */
class GeminiRoleDeclarationsTest {

    private static final String PROSE = """
            {"candidates":[{"content":{"parts":[{"text":"ok"}]}}]}""";

    private HttpServer server;
    private final AtomicReference<String> lastBody = new AtomicReference<>("");

    private GeminiHttpClient clientAgainstStub() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1beta/models/test-model:generateContent", exchange -> {
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = PROSE.getBytes(StandardCharsets.UTF_8);
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
    void adminToolsAreDeclaredOnlyForAdmins() throws Exception {
        GeminiHttpClient client = clientAgainstStub();

        client.replyWithTools("ctx", List.of(), "hi", (n, a) -> "rows", false);
        assertThat(lastBody.get()).doesNotContain("list_pending_approvals");
        assertThat(lastBody.get()).contains("search_associates"); // base set still there

        client.replyWithTools("ctx", List.of(), "hi", (n, a) -> "rows", true);
        assertThat(lastBody.get()).contains("list_pending_approvals").contains("get_audit_history");
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }
}
