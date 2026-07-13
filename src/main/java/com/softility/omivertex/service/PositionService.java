package com.softility.omivertex.service;

import com.softility.omivertex.domain.*;
import com.softility.omivertex.repository.AllocationRepository;
import com.softility.omivertex.repository.AssociateRepository;
import com.softility.omivertex.repository.OpenPositionRepository;
import com.softility.omivertex.repository.PositionSkillRepository;
import com.softility.omivertex.repository.ProjectRepository;
import com.softility.omivertex.repository.SkillRepository;
import com.softility.omivertex.repository.AssociateSkillRepository;
import com.softility.omivertex.web.dto.*;
import com.softility.omivertex.web.error.BadRequestException;
import com.softility.omivertex.web.error.ConflictException;
import com.softility.omivertex.web.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class PositionService {

    /** How many candidates the matcher returns. */
    static final int MAX_MATCH_CANDIDATES = 10;

    private final OpenPositionRepository positions;
    private final ProjectRepository projects;
    private final AssociateRepository associates;
    private final AllocationRepository allocations;
    private final AllocationService allocationService;
    private final AuditService auditService;
    private final SkillRepository skillRepository;
    private final AssociateSkillRepository associateSkillRepository;
    private final PositionSkillRepository positionSkills;

    public PositionService(OpenPositionRepository positions, ProjectRepository projects,
                           AssociateRepository associates, AllocationRepository allocations,
                           AllocationService allocationService, AuditService auditService,
                           SkillRepository skillRepository, AssociateSkillRepository associateSkillRepository,
                           PositionSkillRepository positionSkills) {
        this.positions = positions;
        this.projects = projects;
        this.associates = associates;
        this.allocations = allocations;
        this.allocationService = allocationService;
        this.auditService = auditService;
        this.skillRepository = skillRepository;
        this.associateSkillRepository = associateSkillRepository;
        this.positionSkills = positionSkills;
    }

    @Transactional(readOnly = true)
    public List<PositionResponse> list(PositionStatus status, Long projectId) {
        Map<Long, List<PositionSkill>> skillsByPosition = positionSkills.findAllWithDetails().stream()
                .collect(Collectors.groupingBy(ps -> ps.getPosition().getId()));
        return positions.findAllWithDetails().stream()
                .filter(p -> status == null || p.getStatus() == status)
                .filter(p -> projectId == null || p.getProject().getId().equals(projectId))
                .map(p -> PositionResponse.from(p, skillsByPosition.getOrDefault(p.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PositionResponse get(Long id) {
        return PositionResponse.from(find(id), positionSkills.findByPositionId(id));
    }

    public PositionResponse create(PositionRequest request) {
        OpenPosition position = new OpenPosition();
        apply(position, request);
        position = positions.save(position);
        replaceSkills(position, request.skills());
        auditService.record("CREATED", "Position", position.getId(), "Opened position " + position.getTitle() + " on " + position.getProject().getName());
        return PositionResponse.from(position, positionSkills.findByPositionId(position.getId()));
    }

    public PositionResponse update(Long id, PositionRequest request) {
        OpenPosition position = find(id);
        apply(position, request);
        replaceSkills(position, request.skills());
        auditService.record("UPDATED", "Position", position.getId(), "Updated position " + position.getTitle());
        return PositionResponse.from(position, positionSkills.findByPositionId(id));
    }

    public void delete(Long id) {
        OpenPosition position = find(id);
        positionSkills.deleteByPositionId(id);
        auditService.record("DELETED", "Position", id, "Deleted position " + position.getTitle());
        positions.delete(position);
    }

    /**
     * Candidates with enough free capacity, ranked: full matches (all must-have
     * skills at their minimum proficiency + work-mode fit) first, then partial
     * matches labeled with exactly what is missing; within each group by
     * must-haves met, nice-to-haves met, then longest-benched.
     */
    @Transactional(readOnly = true)
    public List<MatchCandidateResponse> matches(Long id) {
        OpenPosition position = find(id);
        List<PositionSkill> requirements = positionSkills.findByPositionId(id);
        List<Allocation> all = allocations.findAllWithDetails();
        Map<Long, List<Allocation>> byAssociate = all.stream()
                .collect(Collectors.groupingBy(a -> a.getAssociate().getId()));
        Map<Long, List<AssociateSkill>> skillsByAssociate = associateSkillRepository.findAllWithDetails().stream()
                .collect(Collectors.groupingBy(s -> s.getAssociate().getId()));

        record Scored(MatchCandidateResponse dto, int mustMet, int niceMet, Long benchDays) {}

        return associates.findAll().stream()
                .filter(a -> a.getStatus() == EntityStatus.ACTIVE)
                .map(a -> {
                    List<Allocation> history = byAssociate.getOrDefault(a.getId(), List.of());
                    int allocated = history.stream().filter(Allocation::isCurrent)
                            .mapToInt(Allocation::getAllocationPercent).sum();
                    int available = Math.max(0, 100 - allocated);
                    if (available < position.getAllocationPercent()) return null;
                    Long benchDays = AssociateResponse.benchDays(a, history);

                    Map<Long, Proficiency> held = skillsByAssociate.getOrDefault(a.getId(), List.of()).stream()
                            .collect(Collectors.toMap(s -> s.getSkill().getId(), AssociateSkill::getProficiency));

                    List<String> matched = new ArrayList<>();
                    List<String> missing = new ArrayList<>();
                    int mustTotal = 0;
                    int mustMet = 0;
                    int niceMet = 0;
                    for (PositionSkill req : requirements) {
                        Proficiency min = req.getMinProficiency() == null ? Proficiency.NOVICE : req.getMinProficiency();
                        Proficiency has = held.get(req.getSkill().getId());
                        boolean ok = has != null && has.ordinal() >= min.ordinal();
                        if (req.isRequired()) {
                            mustTotal++;
                            if (ok) {
                                mustMet++;
                                matched.add(req.getSkill().getName());
                            } else {
                                missing.add(req.getSkill().getName()
                                        + (req.getMinProficiency() == null ? "" : " (min " + min + ")"));
                            }
                        } else if (ok) {
                            niceMet++;
                            matched.add(req.getSkill().getName());
                        }
                    }
                    boolean workModeOk = position.getWorkMode() == null || position.getWorkMode() == a.getWorkMode();
                    if (!workModeOk) {
                        missing.add(position.getWorkMode().name().toLowerCase() + " required");
                    }

                    boolean full;
                    if (requirements.isEmpty()) {
                        // legacy fallback: positions without structured skills match on the
                        // free-text headline (deliberate exception, see docs/TODO.md)
                        boolean textHit = matchesSkill(a, position.getRequiredSkill());
                        if (textHit) {
                            matched.add(position.getRequiredSkill());
                            mustMet++;
                        } else if (position.getRequiredSkill() != null && !position.getRequiredSkill().isBlank()) {
                            missing.add(position.getRequiredSkill());
                        }
                        full = textHit && workModeOk;
                    } else {
                        full = mustMet == mustTotal && workModeOk;
                    }
                    return new Scored(new MatchCandidateResponse(a.getId(), a.getName(), a.getDesignation(),
                            benchDays, available, full, matched, missing), mustMet, niceMet, benchDays);
                })
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparing((Scored s) -> s.dto().fullMatch(), Comparator.reverseOrder())
                        .thenComparing(Scored::mustMet, Comparator.reverseOrder())
                        .thenComparing(Scored::niceMet, Comparator.reverseOrder())
                        .thenComparing(s -> s.benchDays() == null ? -1L : s.benchDays(), Comparator.reverseOrder())
                        .thenComparing(s -> s.dto().name()))
                .limit(MAX_MATCH_CANDIDATES)
                .map(Scored::dto)
                .toList();
    }

    /** Allocates the associate per the position's terms and marks it FILLED. */
    public PositionResponse fill(Long id, FillPositionRequest request) {
        OpenPosition position = find(id);
        if (position.getStatus() != PositionStatus.OPEN) {
            throw new ConflictException("Position '" + position.getTitle() + "' is not open");
        }
        // the allocation mirrors the seat's engagement window: capacity is consumed
        // only for that period and the end date feeds the roll-off radar
        LocalDate start = position.getStartDate() == null ? LocalDate.now() : position.getStartDate();
        allocationService.create(new AllocationRequest(request.associateId(),
                position.getProject().getId(), position.isBillable(),
                position.getAllocationPercent(), start, position.getEndDate()));
        position.setStatus(PositionStatus.FILLED);
        auditService.record("FILLED", "Position", position.getId(),
                "Filled position " + position.getTitle() + " with associate id " + request.associateId());
        return PositionResponse.from(position, positionSkills.findByPositionId(id));
    }

    private static boolean matchesSkill(Associate associate, String requiredSkill) {
        if (requiredSkill == null || requiredSkill.isBlank()) return false;
        String needle = requiredSkill.toLowerCase();
        return (associate.getPrimarySkill() != null && associate.getPrimarySkill().toLowerCase().contains(needle))
                || (associate.getSecondarySkill() != null && associate.getSecondarySkill().toLowerCase().contains(needle));
    }

    private OpenPosition find(Long id) {
        return positions.findById(id).orElseThrow(() -> new NotFoundException("Position", id));
    }

    private void replaceSkills(OpenPosition position, List<PositionRequest.SkillReq> reqs) {
        positionSkills.deleteByPositionId(position.getId());
        if (reqs == null) return;
        Set<Long> seen = new HashSet<>();
        for (PositionRequest.SkillReq req : reqs) {
            if (!seen.add(req.skillId())) {
                throw new BadRequestException("Duplicate skill in requirements");
            }
            Skill skill = skillRepository.findById(req.skillId())
                    .orElseThrow(() -> new NotFoundException("Skill", req.skillId()));
            PositionSkill ps = new PositionSkill();
            ps.setPosition(position);
            ps.setSkill(skill);
            ps.setMinProficiency(req.minProficiency());
            ps.setRequired(req.required() == null || req.required());
            positionSkills.save(ps);
        }
    }

    private void apply(OpenPosition position, PositionRequest request) {
        Project project = projects.findById(request.projectId())
                .orElseThrow(() -> new NotFoundException("Project", request.projectId()));
        position.setTitle(request.title());
        position.setProject(project);
        position.setRequiredSkill(request.requiredSkill());
        position.setWorkMode(request.workMode());
        position.setBillable(request.billable() == null || request.billable());
        position.setAllocationPercent(request.allocationPercent() == null ? 100 : request.allocationPercent());
        position.setHeadcount(request.headcount() == null ? 1 : request.headcount());
        if (request.startDate() != null && request.endDate() != null
                && request.endDate().isBefore(request.startDate())) {
            throw new BadRequestException("End date cannot be before start date");
        }
        position.setStartDate(request.startDate());
        position.setEndDate(request.endDate());
        position.setStatus(request.status() == null ? PositionStatus.OPEN : request.status());
    }
}
