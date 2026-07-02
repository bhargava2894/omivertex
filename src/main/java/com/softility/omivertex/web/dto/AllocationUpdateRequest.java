package com.softility.omivertex.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record AllocationUpdateRequest(
        @NotNull(message = "Billable flag is required") Boolean billable,
        @Min(value = 1, message = "Allocation must be at least 1%")
        @Max(value = 100, message = "Allocation cannot exceed 100%")
        Integer allocationPercent,
        @NotNull(message = "Start date is required") LocalDate startDate,
        LocalDate endDate) {
}
