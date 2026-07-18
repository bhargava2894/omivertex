package com.softility.omivertex.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** One previous (external) employer; dates are nullable — résumés say "Present". */
public record EmploymentEntry(
        @NotBlank(message = "Company is required") @Size(max = 120) String company,
        @Size(max = 120) String title,
        LocalDate startDate,
        LocalDate endDate) {
}
