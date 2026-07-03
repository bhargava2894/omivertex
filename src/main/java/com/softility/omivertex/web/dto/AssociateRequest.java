package com.softility.omivertex.web.dto;

import com.softility.omivertex.domain.EntityStatus;
import com.softility.omivertex.domain.WorkMode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AssociateRequest(
        @NotBlank(message = "Name is required") String name,
        @NotBlank(message = "Email is required") @Email(message = "Email must be valid") String email,
        @NotBlank(message = "Company is required") String company,
        String location,
        @NotNull(message = "Work mode is required") WorkMode workMode,
        String designation,
        String primarySkill,
        String secondarySkill,
        EntityStatus status) {
}
