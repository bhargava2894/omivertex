package com.softility.omivertex.web.dto;

import com.softility.omivertex.domain.EntityStatus;
import com.softility.omivertex.domain.WorkMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AssociateRequest(
        @NotBlank(message = "Name is required") String name,
        @NotBlank(message = "Email is required") @Email(message = "Email must be valid") String email,
        @NotBlank(message = "Company is required") String company,
        String location,
        @NotNull(message = "Work mode is required") WorkMode workMode,
        String designation,
        java.time.LocalDate joinedDate,
        java.time.LocalDate resignationDate,
        java.time.LocalDate lastWorkingDay,
        com.softility.omivertex.domain.ExitReason exitReason,
        // The rated skills to attach. Optional (null leaves skills unchanged on update,
        // empty on create). The primarySkill/secondarySkill headline is derived from these.
        @Valid List<SkillAssignmentRequest.Entry> skills,
        EntityStatus status) {
}
