package com.softility.omivertex.service;

import com.softility.omivertex.domain.Allocation;
import com.softility.omivertex.domain.Associate;
import com.softility.omivertex.domain.AssociateSkill;
import com.softility.omivertex.domain.EntityStatus;
import com.softility.omivertex.domain.OpenPosition;
import com.softility.omivertex.domain.PositionSkill;
import com.softility.omivertex.domain.PositionStatus;
import com.softility.omivertex.domain.Proficiency;
import com.softility.omivertex.domain.Skill;
import com.softility.omivertex.repository.AllocationRepository;
import com.softility.omivertex.repository.AssociateRepository;
import com.softility.omivertex.repository.AssociateSkillRepository;
import com.softility.omivertex.repository.PositionSkillRepository;
import com.softility.omivertex.repository.SkillRepository;
import com.softility.omivertex.web.dto.AssociateResponse;
import com.softility.omivertex.web.dto.DashboardSummaryResponse;
import com.softility.omivertex.web.dto.SkillGapDetailResponse;
import com.softility.omivertex.web.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
    private final SkillRepository skillRepository;

    public SkillGapService(AssociateRepository associateRepository,
                           AllocationRepository allocationRepository,
                           PositionSkillRepository positionSkillRepository,
                           AssociateSkillRepository associateSkillRepository,
                           SkillRepository skillRepository) {
        this.associateRepository = associateRepository;
        this.allocationRepository = allocationRepository;
        this.positionSkillRepository = positionSkillRepository;
        this.associateSkillRepository = associateSkillRepository;
        this.skillRepository = skillRepository;
    }

    /** Dashboard panel: demand-only rows, worst gap first, capped. */
    public List<DashboardSummaryResponse.SkillGap> dashboardPanel() {
        return compute(false).stream().limit(DASHBOARD_ROW_CAP).toList();
    }

    /** Full report: also skills with rated supply but no open demand (surplus rows). */
    public List<DashboardSummaryResponse.SkillGap> fullReport() {
        return compute(true);
    }

    /**
     * The people and positions behind one gap row. Uses the same threshold rule as
     * {@link #compute}, so the lists here always add up to the numbers on the row.
     */
    public SkillGapDetailResponse detail(Long skillId) {
        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new NotFoundException("Skill", skillId));

        List<PositionSkill> reqs = positionSkillRepository.findAllWithDetails().stream()
                .filter(PositionSkill::isRequired)
                .filter(ps -> ps.getPosition().getStatus() == PositionStatus.OPEN)
                .filter(ps -> ps.getSkill().getId().equals(skillId))
                .toList();
        Proficiency threshold = threshold(reqs);

        Map<Long, List<Allocation>> allocationsByAssociate = allocationRepository.findAllWithDetails().stream()
                .collect(Collectors.groupingBy(a -> a.getAssociate().getId()));
        List<AssociateSkill> ratings = associateSkillRepository.findAllWithDetails().stream()
                .filter(as -> as.getSkill().getId().equals(skillId))
                .filter(as -> as.getAssociate().getStatus() == EntityStatus.ACTIVE)
                .sorted(Comparator.comparing((AssociateSkill as) -> as.getProficiency().ordinal()).reversed()
                        .thenComparing(as -> as.getAssociate().getName()))
                .toList();

        List<SkillGapDetailResponse.DemandPosition> openDemand = reqs.stream()
                .map(ps -> {
                    OpenPosition p = ps.getPosition();
                    return new SkillGapDetailResponse.DemandPosition(p.getId(), p.getTitle(),
                            p.getProject().getName(), p.getProject().getClient().getName(),
                            p.getHeadcount(), ps.getMinProficiency(), p.getStartDate());
                })
                .sorted(Comparator.comparing(SkillGapDetailResponse.DemandPosition::title))
                .toList();

        List<SkillGapDetailResponse.BenchHolder> benchSupply = ratings.stream()
                .filter(as -> qualifies(as, threshold))
                .filter(as -> !isAllocated(as.getAssociate(), allocationsByAssociate))
                .map(as -> new SkillGapDetailResponse.BenchHolder(as.getAssociate().getId(),
                        as.getAssociate().getName(), as.getAssociate().getDesignation(), as.getProficiency(),
                        AssociateResponse.benchDays(as.getAssociate(),
                                allocationsByAssociate.getOrDefault(as.getAssociate().getId(), List.of()))))
                .toList();

        LocalDate horizon = LocalDate.now().plusDays(DashboardService.ROLLOFF_HORIZON_DAYS);
        List<SkillGapDetailResponse.RollingOffHolder> rollingOff = ratings.stream()
                .filter(as -> qualifies(as, threshold))
                .flatMap(as -> allocationsByAssociate.getOrDefault(as.getAssociate().getId(), List.of()).stream()
                        .filter(Allocation::isCurrent)
                        .filter(a -> a.getEndDate() != null && !a.getEndDate().isAfter(horizon))
                        .min(Comparator.comparing(Allocation::getEndDate))
                        .map(a -> new SkillGapDetailResponse.RollingOffHolder(as.getAssociate().getId(),
                                as.getAssociate().getName(), as.getAssociate().getDesignation(),
                                as.getProficiency(), a.getProject().getName(), a.getEndDate()))
                        .stream())
                .sorted(Comparator.comparing(SkillGapDetailResponse.RollingOffHolder::endDate))
                .toList();

        List<SkillGapDetailResponse.NearMissHolder> nearMiss = threshold == Proficiency.NOVICE ? List.of()
                : ratings.stream()
                        .filter(as -> as.getProficiency().ordinal() == threshold.ordinal() - 1)
                        .map(as -> new SkillGapDetailResponse.NearMissHolder(as.getAssociate().getId(),
                                as.getAssociate().getName(), as.getAssociate().getDesignation(),
                                as.getProficiency(), threshold,
                                !isAllocated(as.getAssociate(), allocationsByAssociate)))
                        .toList();

        return new SkillGapDetailResponse(skill.getId(), skill.getName(), skill.getCategory().getName(),
                threshold, openDemand, benchSupply, rollingOff, nearMiss);
    }

    /** Lowest proficiency any open position demands; NOVICE when nothing is open. */
    private Proficiency threshold(List<PositionSkill> reqs) {
        return reqs.stream()
                .map(r -> r.getMinProficiency() == null ? Proficiency.NOVICE : r.getMinProficiency())
                .min(Comparator.comparingInt(Enum::ordinal))
                .orElse(Proficiency.NOVICE);
    }

    private boolean qualifies(AssociateSkill rating, Proficiency threshold) {
        return rating.getProficiency().ordinal() >= threshold.ordinal();
    }

    private boolean isAllocated(Associate associate, Map<Long, List<Allocation>> allocationsByAssociate) {
        return allocationsByAssociate.getOrDefault(associate.getId(), List.of()).stream()
                .anyMatch(Allocation::isCurrent);
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
                    Proficiency threshold = threshold(reqs);
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
