package com.softility.omivertex.service;

import com.softility.omivertex.domain.Proficiency;
import com.softility.omivertex.web.error.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiHttpClientTest {

    @Test
    void withoutApiKey_failsClosedWithClearMessage() {
        GeminiHttpClient client = new GeminiHttpClient("", "gemini-2.5-flash",
                "https://generativelanguage.googleapis.com",
                java.time.Duration.ofSeconds(5), java.time.Duration.ofSeconds(30));
        assertThatThrownBy(() -> client.replyWithTools("context", List.of(), "who is on the bench?", null, false))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void parseExtraction_mapsSkillsAndSummary_droppingUnknownIdsAndBadProficiency() {
        List<GeminiClient.SkillOption> taxonomy = List.of(
                new GeminiClient.SkillOption(1L, "Java"),
                new GeminiClient.SkillOption(2L, "React"));
        String json = """
                {"skills":[
                   {"skillId":1,"proficiency":"ADVANCE","evidence":"led a Java team"},
                   {"skillId":2,"proficiency":"WIZARD","evidence":"built SPAs"},
                   {"skillId":99,"proficiency":"MASTERY","evidence":"not in taxonomy"}],
                 "experienceSummary":"8 years"}""";

        GeminiClient.ResumeExtraction out = GeminiHttpClient.parseExtraction(json, taxonomy);

        assertThat(out.experienceSummary()).isEqualTo("8 years");
        assertThat(out.skills()).hasSize(2);
        assertThat(out.skills().get(0).skillId()).isEqualTo(1L);
        assertThat(out.skills().get(0).proficiency()).isEqualTo(Proficiency.ADVANCE);
        assertThat(out.skills().get(0).evidence()).isEqualTo("led a Java team");
        // unknown proficiency degrades to INTERMEDIATE rather than dropping the skill
        assertThat(out.skills().get(1).proficiency()).isEqualTo(Proficiency.INTERMEDIATE);
    }

    @Test
    void parseExtraction_malformedJson_throwsBadRequest() {
        assertThatThrownBy(() -> GeminiHttpClient.parseExtraction("not json", List.of()))
                .isInstanceOf(BadRequestException.class);
    }
}
