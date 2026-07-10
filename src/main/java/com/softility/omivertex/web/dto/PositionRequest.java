package com.softility.omivertex.web.dto;

import com.softility.omivertex.domain.PositionStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record PositionRequest(
        @NotBlank(message = "Title is required") String title,
        @NotNull(message = "Project is required") Long projectId,
        String requiredSkill,
        Long requiredSkillId,
        com.softility.omivertex.domain.Proficiency minProficiency,
        Boolean billable,
        @Min(value = 1, message = "Allocation must be at least 1%")
        @Max(value = 100, message = "Allocation cannot exceed 100%")
        Integer allocationPercent,
        LocalDate startDate,
        LocalDate endDate,
        PositionStatus status) {
}
