package com.softility.omivertex.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public final class TaxonomyDtos {

    private TaxonomyDtos() {
    }

    public record CategoryRequest(@NotBlank(message = "Name is required") String name) {
    }

    public record SkillRequest(
            @NotBlank(message = "Name is required") String name,
            @NotNull(message = "Category is required") Long categoryId) {
    }

    public record SkillResponse(Long id, String name) {
    }

    public record CategoryResponse(Long id, String name, List<SkillResponse> skills) {
    }
}
