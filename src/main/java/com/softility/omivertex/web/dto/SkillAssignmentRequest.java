package com.softility.omivertex.web.dto;

import com.softility.omivertex.domain.Proficiency;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Replaces an associate's full rated-skill set. */
public record SkillAssignmentRequest(@NotNull @Valid List<Entry> skills) {

    public record Entry(
            @NotNull(message = "Skill is required") Long skillId,
            @NotNull(message = "Proficiency is required") Proficiency proficiency) {
    }
}
