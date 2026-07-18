package com.softility.omivertex.web.dto;

import com.softility.omivertex.domain.Proficiency;

import java.time.Instant;
import java.util.List;

public final class ResumeDtos {

    private ResumeDtos() {
    }

    /** How the suggestions were produced — AI (Gemini) or the keyword matcher fallback. */
    public enum SuggestionSource { AI, KEYWORD }

    /** proficiency/evidence are AI-only; null on the keyword path. */
    public record SuggestedSkill(Long skillId, String skillName, String categoryName,
                                 Proficiency proficiency, String evidence) {
    }

    public record ParsedResumeResponse(List<SuggestedSkill> suggestedSkills, boolean textExtracted,
                                       String experienceSummary, SuggestionSource source,
                                       String name, String phone,
                                       List<EmploymentEntry> employmentHistory) {
    }

    public record ResumeMetaResponse(String filename, String contentType, long byteSize, Instant uploadedAt) {
    }
}
