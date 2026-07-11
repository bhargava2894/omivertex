package com.softility.omivertex.web.dto;

import java.util.List;

public record MatchCandidateResponse(
        Long associateId,
        String name,
        String designation,
        Long benchDays,
        int availablePercent,
        boolean fullMatch,
        List<String> matchedSkills,
        List<String> missingRequirements) {
}
