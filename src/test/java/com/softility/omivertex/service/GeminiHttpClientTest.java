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

    @Test
    void parseExtraction_readsProfileAndEmployment_lenientOnDates() {
        String json = """
                {"name":"Priya Sharma","phone":"+91 98765 43210",
                 "employment":[
                   {"company":"Globex","title":"Senior Engineer","startDate":"2021-03-01","endDate":null},
                   {"company":"Initech","title":null,"startDate":"garbage","endDate":"2020-12-01"},
                   {"company":"","title":"Dropped — no company","startDate":null,"endDate":null}],
                 "skills":[],"experienceSummary":"8 years across two firms."}""";

        GeminiClient.ResumeExtraction extraction =
                GeminiHttpClient.parseExtraction(json, List.of());

        assertThat(extraction.name()).isEqualTo("Priya Sharma");
        assertThat(extraction.phone()).isEqualTo("+91 98765 43210");
        assertThat(extraction.employment()).hasSize(2); // companyless row dropped
        assertThat(extraction.employment().get(0).company()).isEqualTo("Globex");
        assertThat(extraction.employment().get(0).endDate()).isNull();
        assertThat(extraction.employment().get(1).startDate()).isNull(); // "garbage" degraded to null
        assertThat(extraction.employment().get(1).endDate()).isEqualTo(java.time.LocalDate.of(2020, 12, 1));
    }

    @Test
    void parseExtraction_implausiblyLongPhone_isNulledAsNoise() {
        String json = """
                {"phone":"call between 9 and 5 on +91 98765 43210 or the office line",
                 "skills":[],"experienceSummary":"s"}""";

        GeminiClient.ResumeExtraction extraction =
                GeminiHttpClient.parseExtraction(json, List.of());

        // a "phone" that long is extraction noise, not a number to salvage
        assertThat(extraction.phone()).isNull();
    }

    @Test
    void parseExtraction_missingProfileFields_areNull() {
        String json = """
                {"skills":[],"experienceSummary":"s"}""";

        GeminiClient.ResumeExtraction extraction =
                GeminiHttpClient.parseExtraction(json, List.of());

        assertThat(extraction.name()).isNull();
        assertThat(extraction.phone()).isNull();
        assertThat(extraction.employment()).isEmpty();
    }
}
