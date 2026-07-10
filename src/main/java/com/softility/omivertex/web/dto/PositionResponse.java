package com.softility.omivertex.web.dto;

import com.softility.omivertex.domain.OpenPosition;
import com.softility.omivertex.domain.PositionStatus;

import java.time.LocalDate;

public record PositionResponse(
        Long id,
        String title,
        Long projectId,
        String projectName,
        String clientName,
        String requiredSkill,
        Long requiredSkillId,
        String requiredSkillName,
        com.softility.omivertex.domain.Proficiency minProficiency,
        boolean billable,
        int allocationPercent,
        LocalDate startDate,
        LocalDate endDate,
        PositionStatus status) {

    public static PositionResponse from(OpenPosition position) {
        return new PositionResponse(position.getId(), position.getTitle(),
                position.getProject().getId(), position.getProject().getName(),
                position.getProject().getClient().getName(),
                position.getRequiredSkill(),
                position.getRequiredSkillRef() == null ? null : position.getRequiredSkillRef().getId(),
                position.getRequiredSkillRef() == null ? null : position.getRequiredSkillRef().getName(),
                position.getMinProficiency(),
                position.isBillable(),
                position.getAllocationPercent(), position.getStartDate(), position.getEndDate(),
                position.getStatus());
    }
}
