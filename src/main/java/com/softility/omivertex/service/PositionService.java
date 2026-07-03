package com.softility.omivertex.service;

import com.softility.omivertex.domain.*;
import com.softility.omivertex.repository.AllocationRepository;
import com.softility.omivertex.repository.AssociateRepository;
import com.softility.omivertex.repository.OpenPositionRepository;
import com.softility.omivertex.repository.ProjectRepository;
import com.softility.omivertex.web.dto.*;
import com.softility.omivertex.web.error.ConflictException;
import com.softility.omivertex.web.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class PositionService {

    private final OpenPositionRepository positions;
    private final ProjectRepository projects;
    private final AssociateRepository associates;
    private final AllocationRepository allocations;
    private final AllocationService allocationService;

    private final AuditService auditService;

    public PositionService(OpenPositionRepository positions, ProjectRepository projects,
                           AssociateRepository associates, AllocationRepository allocations,
                           AllocationService allocationService, AuditService auditService) {
        this.positions = positions;
        this.projects = projects;
        this.associates = associates;
        this.allocations = allocations;
        this.allocationService = allocationService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<PositionResponse> list(PositionStatus status, Long projectId) {
        return positions.findAllWithDetails().stream()
                .filter(p -> status == null || p.getStatus() == status)
                .filter(p -> projectId == null || p.getProject().getId().equals(projectId))
                .map(PositionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PositionResponse get(Long id) {
        return PositionResponse.from(find(id));
    }

    public PositionResponse create(PositionRequest request) {
        OpenPosition position = new OpenPosition();
        apply(position, request);
        position = positions.save(position);
        auditService.record("CREATED", "Position", position.getId(), "Opened position " + position.getTitle() + " on " + position.getProject().getName());
        return PositionResponse.from(position);
    }

    public PositionResponse update(Long id, PositionRequest request) {
        OpenPosition position = find(id);
        apply(position, request);
        auditService.record("UPDATED", "Position", position.getId(), "Updated position " + position.getTitle());
        return PositionResponse.from(position);
    }

    public void delete(Long id) {
        OpenPosition position = find(id);
        auditService.record("DELETED", "Position", id, "Deleted position " + position.getTitle());
        positions.delete(position);
    }

    /**
     * Candidates with enough free capacity for the position, ranked: skill match
     * first, then bench before partially-allocated, then longest-benched.
     */
    @Transactional(readOnly = true)
    public List<MatchCandidateResponse> matches(Long id) {
        OpenPosition position = find(id);
        List<Allocation> all = allocations.findAllWithDetails();
        Map<Long, List<Allocation>> byAssociate = all.stream()
                .collect(Collectors.groupingBy(a -> a.getAssociate().getId()));

        return associates.findAll().stream()
                .filter(a -> a.getStatus() == EntityStatus.ACTIVE)
                .map(a -> {
                    List<Allocation> history = byAssociate.getOrDefault(a.getId(), List.of());
                    int allocated = history.stream().filter(Allocation::isCurrent)
                            .mapToInt(Allocation::getAllocationPercent).sum();
                    int available = Math.max(0, 100 - allocated);
                    Long benchDays = AssociateResponse.benchDays(a, history);
                    boolean skillMatch = matchesSkill(a, position.getRequiredSkill());
                    int score = (skillMatch ? 2 : 0) + (benchDays != null ? 1 : 0);
                    return new MatchCandidateResponse(a.getId(), a.getName(), a.getDesignation(),
                            a.getPrimarySkill(), a.getSecondarySkill(), benchDays, available, skillMatch, score);
                })
                .filter(c -> c.availablePercent() >= position.getAllocationPercent())
                .sorted(Comparator.comparingInt(MatchCandidateResponse::score).reversed()
                        .thenComparing(c -> c.benchDays() == null ? -1L : c.benchDays(), Comparator.reverseOrder())
                        .thenComparing(MatchCandidateResponse::name))
                .limit(10)
                .toList();
    }

    /** Allocates the associate per the position's terms and marks it FILLED. */
    public PositionResponse fill(Long id, FillPositionRequest request) {
        OpenPosition position = find(id);
        if (position.getStatus() != PositionStatus.OPEN) {
            throw new ConflictException("Position '" + position.getTitle() + "' is not open");
        }
        // filling commits the associate immediately — capacity is consumed from today,
        // regardless of the seat's nominal start date
        LocalDate start = LocalDate.now();
        allocationService.create(new AllocationRequest(request.associateId(),
                position.getProject().getId(), position.isBillable(),
                position.getAllocationPercent(), start, null));
        position.setStatus(PositionStatus.FILLED);
        auditService.record("FILLED", "Position", position.getId(),
                "Filled position " + position.getTitle() + " with associate id " + request.associateId());
        return PositionResponse.from(position);
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

    private void apply(OpenPosition position, PositionRequest request) {
        Project project = projects.findById(request.projectId())
                .orElseThrow(() -> new NotFoundException("Project", request.projectId()));
        position.setTitle(request.title());
        position.setProject(project);
        position.setRequiredSkill(request.requiredSkill());
        position.setBillable(request.billable() == null || request.billable());
        position.setAllocationPercent(request.allocationPercent() == null ? 100 : request.allocationPercent());
        position.setStartDate(request.startDate());
        position.setStatus(request.status() == null ? PositionStatus.OPEN : request.status());
    }
}
