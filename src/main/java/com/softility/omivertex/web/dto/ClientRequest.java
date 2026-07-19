package com.softility.omivertex.web.dto;

import com.softility.omivertex.domain.EntityStatus;
import jakarta.validation.constraints.NotBlank;

public record ClientRequest(
        @NotBlank(message = "Name is required") String name,
        @NotBlank(message = "Client ID is required") String clientId,
        String industry,
        String location,
        EntityStatus status) {
}
