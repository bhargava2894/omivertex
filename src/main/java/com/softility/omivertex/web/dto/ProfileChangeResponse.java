package com.softility.omivertex.web.dto;

import com.softility.omivertex.domain.Proficiency;
import com.softility.omivertex.domain.ProfileChangeStatus;
import com.softility.omivertex.domain.ProfileChangeType;

import java.time.Instant;
import java.util.List;

public record ProfileChangeResponse(
        Long id,
        Long associateId,
        String associateName,
        ProfileChangeType type,
        ProfileChangeStatus status,
        List<ProposedSkill> proposedSkills,
        String resumeFilename,
        Long resumeByteSize,
        String note,
        Instant createdAt,
        Instant decidedAt,
        String decidedBy) {

    public record ProposedSkill(String skillName, Proficiency proficiency, boolean primary) {
    }
}
