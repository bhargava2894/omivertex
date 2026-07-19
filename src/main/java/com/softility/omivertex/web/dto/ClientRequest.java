package com.softility.omivertex.web.dto;

import com.softility.omivertex.domain.EntityStatus;
import jakarta.validation.constraints.NotBlank;

public record ClientRequest(
        @NotBlank(message = "Name is required") String name,
        String clientId,
        String industry,
        String location,
        EntityStatus status) {
}
