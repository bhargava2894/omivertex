package com.softility.omivertex.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AssistantChatRequest(
        @NotBlank(message = "Message is required")
        @Size(max = 2000, message = "Message cannot exceed 2000 characters")
        String message,
        @Valid List<HistoryTurn> history) {

    public record HistoryTurn(String role, String content) {}
}
