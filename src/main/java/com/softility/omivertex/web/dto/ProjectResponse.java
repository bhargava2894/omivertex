package com.softility.omivertex.web.dto;

import com.softility.omivertex.domain.Project;
import com.softility.omivertex.domain.ProjectStatus;

import java.time.LocalDate;

public record ProjectResponse(
        Long id,
        String code,
        String name,
        Long clientId,
        String clientName,
        ProjectStatus status,
        LocalDate startDate,
        LocalDate endDate) {

    public static ProjectResponse from(Project project) {
        return new ProjectResponse(project.getId(), project.getCode(), project.getName(),
                project.getClient().getId(), project.getClient().getName(),
                project.getStatus(), project.getStartDate(), project.getEndDate());
    }
}
