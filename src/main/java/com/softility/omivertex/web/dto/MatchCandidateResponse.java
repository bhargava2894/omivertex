package com.softility.omivertex.web.dto;

import com.softility.omivertex.domain.Proficiency;

public record MatchCandidateResponse(
        Long associateId,
        String name,
        String designation,
        String primarySkill,
        String secondarySkill,
        Long benchDays,
        int availablePercent,
        boolean skillMatch,
        int score,
        Proficiency matchedProficiency) {
}
