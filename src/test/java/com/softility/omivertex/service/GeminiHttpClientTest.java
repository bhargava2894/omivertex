package com.softility.omivertex.service;

import com.softility.omivertex.web.error.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiHttpClientTest {

    @Test
    void withoutApiKey_failsClosedWithClearMessage() {
        GeminiHttpClient client = new GeminiHttpClient("", "gemini-2.5-flash");
        assertThatThrownBy(() -> client.reply("context", List.of(), "who is on the bench?"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not configured");
    }
}
