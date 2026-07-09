package com.softility.omivertex.web.dto;

import java.time.Instant;
import java.util.List;

public final class ResumeDtos {

    private ResumeDtos() {
    }

    public record SuggestedSkill(Long skillId, String skillName, String categoryName) {
    }

    public record ParsedResumeResponse(List<SuggestedSkill> suggestedSkills, boolean textExtracted) {
    }

    public record ResumeMetaResponse(String filename, String contentType, long byteSize, Instant uploadedAt) {
    }
}
