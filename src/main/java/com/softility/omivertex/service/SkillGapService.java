package com.softility.omivertex.service;

import com.softility.omivertex.domain.Allocation;
import com.softility.omivertex.domain.Associate;
import com.softility.omivertex.domain.AssociateSkill;
import com.softility.omivertex.domain.EntityStatus;
import com.softility.omivertex.domain.PositionSkill;
import com.softility.omivertex.domain.PositionStatus;
import com.softility.omivertex.domain.Proficiency;
import com.softility.omivertex.domain.Skill;
import com.softility.omivertex.repository.AllocationRepository;
import com.softility.omivertex.repository.AssociateRepository;
import com.softility.omivertex.repository.AssociateSkillRepository;
import com.softility.omivertex.repository.PositionSkillRepository;
import com.softility.omivertex.web.dto.DashboardSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One source of truth for skill supply-vs-demand math. Demand = required
 * skills on OPEN positions; supply counts ACTIVE associates at or above the
 * lowest demanded proficiency (any proficiency when there is no demand);
 * gap = demand - benchSupply (positive = hire or train, negative = spare).
 */
@Service
@Transactional(readOnly = true)
public class SkillGapService {

    /** Cap on the dashboard skill-gap panel rows (the full report is uncapped). */
    static final int DASHBOARD_ROW_CAP = 20;

    private final AssociateRepository associateRepository;
    private final AllocationRepository allocationRepository;
    private final PositionSkillRepository positionSkillRepository;
    private final AssociateSkillRepository associateSkillRepository;

    public SkillGapService(AssociateRepository associateRepository,
                           AllocationRepository allocationRepository,
                           PositionSkillRepository positionSkillRepository,
                           AssociateSkillRepository associateSkillRepository) {
        this.associateRepository = associateRepository;
        this.allocationRepository = allocationRepository;
        this.positionSkillRepository = positionSkillRepository;
        this.associateSkillRepository = associateSkillRepository;
    }

    /** Dashboard panel: demand-only rows, worst gap first, capped. */
    public List<DashboardSummaryResponse.SkillGap> dashboardPanel() {
        return compute(false).stream().limit(DASHBOARD_ROW_CAP).toList();
    }

    /** Full report: also skills with rated supply but no open demand (surplus rows). */
    public List<DashboardSummaryResponse.SkillGap> fullReport() {
        return compute(true);
    }

    private List<DashboardSummaryResponse.SkillGap> compute(boolean includeSurplus) {
        Set<Long> activeIds = associateRepository.findAll().stream()
                .filter(a -> a.getStatus() == EntityStatus.ACTIVE)
                .map(Associate::getId)
                .collect(Collectors.toSet());
        Set<Long> allocatedIds = allocationRepository.findAllWithDetails().stream()
                .filter(Allocation::isCurrent)
                .map(a -> a.getAssociate().getId())
                .filter(activeIds::contains)
                .collect(Collectors.toSet());

        Map<Long, List<PositionSkill>> demandBySkill = positionSkillRepository.findAllWithDetails().stream()
                .filter(PositionSkill::isRequired)
                .filter(ps -> ps.getPosition().getStatus() == PositionStatus.OPEN)
                .collect(Collectors.groupingBy(ps -> ps.getSkill().getId()));
        List<AssociateSkill> ratedSkills = associateSkillRepository.findAllWithDetails();

        Map<Long, Skill> skillsById = new LinkedHashMap<>();
        demandBySkill.values().forEach(reqs ->
                skillsById.putIfAbsent(reqs.get(0).getSkill().getId(), reqs.get(0).getSkill()));
        if (includeSurplus) {
            ratedSkills.forEach(s -> skillsById.putIfAbsent(s.getSkill().getId(), s.getSkill()));
        }

        return skillsById.values().stream()
                .map(skill -> {
                    List<PositionSkill> reqs = demandBySkill.getOrDefault(skill.getId(), List.of());
                    long demand = reqs.stream().mapToLong(ps -> ps.getPosition().getHeadcount()).sum();
                    Proficiency threshold = reqs.stream()
                            .map(r -> r.getMinProficiency() == null ? Proficiency.NOVICE : r.getMinProficiency())
                            .min(Comparator.comparingInt(Enum::ordinal)).orElse(Proficiency.NOVICE);
                    Set<Long> holders = ratedSkills.stream()
                            .filter(s -> s.getSkill().getId().equals(skill.getId()))
                            .filter(s -> s.getProficiency().ordinal() >= threshold.ordinal())
                            .map(s -> s.getAssociate().getId())
                            .filter(activeIds::contains)
                            .collect(Collectors.toSet());
                    long benchSupply = holders.stream().filter(h -> !allocatedIds.contains(h)).count();
                    return new DashboardSummaryResponse.SkillGap(skill.getId(), skill.getName(),
                            skill.getCategory().getName(), demand, benchSupply, holders.size(),
                            demand - benchSupply);
                })
                .sorted(Comparator.comparingLong(DashboardSummaryResponse.SkillGap::gap).reversed()
                        .thenComparing(DashboardSummaryResponse.SkillGap::skillName))
                .toList();
    }
}
