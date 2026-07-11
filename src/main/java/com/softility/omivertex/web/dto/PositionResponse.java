package com.softility.omivertex.web.dto;

import com.softility.omivertex.domain.OpenPosition;
import com.softility.omivertex.domain.PositionSkill;
import com.softility.omivertex.domain.PositionStatus;
import com.softility.omivertex.domain.Proficiency;
import com.softility.omivertex.domain.WorkMode;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

public record PositionResponse(
        Long id,
        String title,
        Long projectId,
        String projectName,
        String clientName,
        String requiredSkill,
        WorkMode workMode,
        List<SkillLine> skills,
        boolean billable,
        int allocationPercent,
        LocalDate startDate,
        LocalDate endDate,
        PositionStatus status) {

    /** One demanded skill on the position; required=false means nice-to-have. */
    public record SkillLine(Long skillId, String skillName, String category,
                            Proficiency minProficiency, boolean required) {
    }

    public static PositionResponse from(OpenPosition position, List<PositionSkill> positionSkills) {
        List<SkillLine> skills = positionSkills.stream()
                .sorted(Comparator.comparing(PositionSkill::isRequired, Comparator.reverseOrder())
                        .thenComparing(ps -> ps.getSkill().getName()))
                .map(ps -> new SkillLine(ps.getSkill().getId(), ps.getSkill().getName(),
                        ps.getSkill().getCategory().getName(), ps.getMinProficiency(), ps.isRequired()))
                .toList();
        return new PositionResponse(position.getId(), position.getTitle(),
                position.getProject().getId(), position.getProject().getName(),
                position.getProject().getClient().getName(),
                position.getRequiredSkill(), position.getWorkMode(), skills,
                position.isBillable(),
                position.getAllocationPercent(), position.getStartDate(), position.getEndDate(),
                position.getStatus());
    }
}
