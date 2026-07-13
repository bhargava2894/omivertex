package com.softility.omivertex.web.dto;

import com.softility.omivertex.domain.Proficiency;

import java.time.LocalDate;
import java.util.List;

/**
 * The people and positions behind a single skill-gap row. {@code threshold} is the
 * lowest proficiency any open position demands (NOVICE when nothing is open), and
 * every list below is derived from it: who is asking, who is free, who is coming
 * free, and who is one level short of qualifying.
 */
public record SkillGapDetailResponse(
        Long skillId,
        String skillName,
        String category,
        Proficiency threshold,
        List<DemandPosition> openDemand,
        List<BenchHolder> benchSupply,
        List<RollingOffHolder> rollingOff,
        List<NearMissHolder> nearMiss) {

    /** An OPEN position that requires this skill — one line of the demand figure. */
    public record DemandPosition(Long positionId, String title, String projectName, String clientName,
                                 int headcount, Proficiency minProficiency, LocalDate startDate) {
    }

    /** A qualified holder with no current allocation — available now. */
    public record BenchHolder(Long associateId, String name, String designation,
                              Proficiency proficiency, Long benchDays) {
    }

    /** A qualified holder whose current allocation ends soon — supply about to free up. */
    public record RollingOffHolder(Long associateId, String name, String designation,
                                   Proficiency proficiency, String projectName, LocalDate endDate) {
    }

    /** Rated exactly one level below the threshold — the train-vs-hire shortlist. */
    public record NearMissHolder(Long associateId, String name, String designation,
                                 Proficiency proficiency, Proficiency requiredProficiency,
                                 boolean onBench) {
    }
}
