package com.softility.omivertex.web.dto;

import com.softility.omivertex.domain.ProjectStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ProjectRequest(
        @NotBlank(message = "Code is required") String code,
        @NotBlank(message = "Name is required") String name,
        @NotNull(message = "Client is required") Long clientId,
        ProjectStatus status,
        LocalDate startDate,
        LocalDate endDate) {
}
