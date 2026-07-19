package com.softility.omivertex.web.dto;

import com.softility.omivertex.domain.PositionStatus;
import com.softility.omivertex.domain.Proficiency;
import com.softility.omivertex.domain.WorkMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record PositionRequest(
        @NotBlank(message = "Title is required") String title,
        @NotNull(message = "Project is required") Long projectId,
        String requiredSkill,
        WorkMode workMode,
        @Valid List<SkillReq> skills,
        Boolean billable,
        @Min(value = 1, message = "Allocation must be at least 1%")
        @Max(value = 100, message = "Allocation cannot exceed 100%")
        Integer allocationPercent,
        @Min(value = 1, message = "Headcount must be at least 1")
        Integer headcount,
        LocalDate startDate,
        LocalDate endDate,
        PositionStatus status,
        String jobDescription) {

    /** One demanded skill: must-have (required=true/null) or nice-to-have. */
    public record SkillReq(
            @NotNull(message = "Skill is required") Long skillId,
            Proficiency minProficiency,
            Boolean required) {
    }
}
