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
        boolean billable,
        int allocationPercent,
        LocalDate startDate,
        PositionStatus status) {

    public static PositionResponse from(OpenPosition position) {
        return new PositionResponse(position.getId(), position.getTitle(),
                position.getProject().getId(), position.getProject().getName(),
                position.getProject().getClient().getName(),
                position.getRequiredSkill(), position.isBillable(),
                position.getAllocationPercent(), position.getStartDate(), position.getStatus());
    }
}
