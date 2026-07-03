package com.softility.omivertex.web.dto;

import jakarta.validation.constraints.NotNull;

public record FillPositionRequest(
        @NotNull(message = "Associate is required") Long associateId) {
}
