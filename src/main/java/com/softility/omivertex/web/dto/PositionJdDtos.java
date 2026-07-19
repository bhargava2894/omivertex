package com.softility.omivertex.web.dto;

import com.softility.omivertex.domain.Proficiency;
import com.softility.omivertex.domain.WorkMode;
import com.softility.omivertex.web.dto.ResumeDtos.SuggestionSource;

import java.time.LocalDate;
import java.util.List;

public final class PositionJdDtos {

    private PositionJdDtos() {
    }

    /** A required-skill suggestion shaped for the position form's skill rows. */
    public record JdSuggestedSkill(Long skillId, String skillName, String categoryName,
                                   Proficiency minProficiency, boolean required) {
    }

    /**
     * Stateless prefill for the Open/Edit Position form. Any field is null/empty
     * when the JD did not state it. {@code unmatchedSkills} are skill names read
     * from the JD but not in the taxonomy — surfaced, never dropped.
     */
    public record ParsedJobDescriptionResponse(String title, List<JdSuggestedSkill> skills,
                                               List<String> unmatchedSkills, String jobDescription,
                                               WorkMode workMode, Integer allocationPercent,
                                               LocalDate startDate, LocalDate endDate,
                                               Long suggestedProjectId, String suggestedProjectName,
                                               boolean textExtracted, SuggestionSource source) {
    }
}
