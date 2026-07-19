package com.softility.omivertex.service;

import com.softility.omivertex.domain.Allocation;
import com.softility.omivertex.domain.Associate;
import com.softility.omivertex.domain.AssociateSkill;
import com.softility.omivertex.domain.EmploymentHistory;
import com.softility.omivertex.domain.Skill;
import com.softility.omivertex.domain.EntityStatus;
import com.softility.omivertex.domain.WorkMode;
import com.softility.omivertex.domain.Proficiency;
import com.softility.omivertex.repository.AllocationRepository;
import com.softility.omivertex.repository.AssociateRepository;
import com.softility.omivertex.repository.AssociateSkillRepository;
import com.softility.omivertex.repository.EmploymentHistoryRepository;
import com.softility.omivertex.repository.SkillRepository;
import com.softility.omivertex.repository.ResumeRepository;
import com.softility.omivertex.web.dto.AssociateRequest;
import com.softility.omivertex.web.dto.AssociateResponse;
import com.softility.omivertex.web.dto.EmploymentEntry;
import com.softility.omivertex.web.dto.SkillAssignmentRequest;
import com.softility.omivertex.web.error.ConflictException;
import com.softility.omivertex.web.error.NotFoundException;
import com.softility.omivertex.web.error.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class AssociateService {

    private final AssociateRepository associateRepository;
    private final AllocationRepository allocationRepository;

    private final AuditService auditService;
    private final AssociateSkillRepository associateSkillRepository;
    private final SkillRepository skillRepository;
    private final ResumeRepository resumeRepository;
    private final EmploymentHistoryRepository employmentHistoryRepository;

    public AssociateService(AssociateRepository associateRepository, AllocationRepository allocationRepository,
                            AuditService auditService, AssociateSkillRepository associateSkillRepository,
                            SkillRepository skillRepository, ResumeRepository resumeRepository,
                            EmploymentHistoryRepository employmentHistoryRepository) {
        this.associateRepository = associateRepository;
        this.allocationRepository = allocationRepository;
        this.auditService = auditService;
        this.associateSkillRepository = associateSkillRepository;
        this.skillRepository = skillRepository;
        this.resumeRepository = resumeRepository;
        this.employmentHistoryRepository = employmentHistoryRepository;
    }

    @Transactional(readOnly = true)
    public List<AssociateResponse> list(WorkMode workMode, Boolean billable, Boolean bench,
                                        Long categoryId, Long skillId, Proficiency minProficiency) {
        Map<Long, List<Allocation>> allocationsByAssociate = allocationRepository.findAllWithDetails().stream()
                .collect(Collectors.groupingBy(a -> a.getAssociate().getId()));
        Map<Long, List<AssociateSkill>> skillsByAssociate = associateSkillRepository.findAllWithDetails().stream()
                .collect(Collectors.groupingBy(s -> s.getAssociate().getId()));
        Map<Long, String> resumeFilenames = resumeRepository.findAllMeta().stream()
                .collect(Collectors.toMap(
                        ResumeRepository.AssociateResumeMeta::getAssociateId,
                        ResumeRepository.AssociateResumeMeta::getFilename,
                        (a, b) -> a
                ));
        return associateRepository.findAll().stream()
                .map(associate -> AssociateResponse.from(associate,
                        allocationsByAssociate.getOrDefault(associate.getId(), List.of()),
                        skillsByAssociate.getOrDefault(associate.getId(), List.of()),
                        resumeFilenames.get(associate.getId())))
                .filter(r -> workMode == null || r.workMode() == workMode)
                .filter(r -> billable == null || r.billable() == billable)
                .filter(r -> bench == null || (r.currentProjectId() == null) == bench)
                .filter(r -> {
                    if (categoryId == null && skillId == null && minProficiency == null) {
                        return true;
                    }
                    List<AssociateSkill> heldSkills = skillsByAssociate.getOrDefault(r.id(), List.of());
                    return heldSkills.stream().anyMatch(s -> {
                        boolean matchCategory = categoryId == null || s.getSkill().getCategory().getId().equals(categoryId);
                        boolean matchSkill = skillId == null || s.getSkill().getId().equals(skillId);
                        boolean matchProficiency = minProficiency == null || s.getProficiency().ordinal() >= minProficiency.ordinal();
                        return matchCategory && matchSkill && matchProficiency;
                    });
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public AssociateResponse get(Long id) {
        Associate associate = find(id);
        String resumeFilename = resumeRepository.findMetaByAssociateId(id)
                .map(ResumeRepository.ResumeMeta::getFilename)
                .orElse(null);
        List<EmploymentEntry> history = employmentHistoryRepository.findByAssociateIdOrderBySortOrderAsc(id).stream()
                .map(h -> new EmploymentEntry(h.getCompany(), h.getTitle(), h.getStartDate(), h.getEndDate()))
                .toList();
        return AssociateResponse.from(associate, allocationRepository.findByAssociateId(id),
                associateSkillRepository.findByAssociateId(id), resumeFilename, history);
    }

    /** Replaces the associate's entire rated-skill set with the given entries. */
    public AssociateResponse replaceSkills(Long id, SkillAssignmentRequest request) {
        Associate associate = find(id);
        long primaries = request.skills().stream().filter(SkillAssignmentRequest.Entry::primary).count();
        if (primaries > 1) {
            throw new BadRequestException("Only one skill can be marked primary.");
        }
        associateSkillRepository.deleteByAssociateId(id);
        associateSkillRepository.flush(); // deletes must hit the DB before re-inserts touch the unique constraint
        List<AssociateSkill> saved = new ArrayList<>();
        for (SkillAssignmentRequest.Entry entry : request.skills()) {
            Skill skill = skillRepository.findById(entry.skillId())
                    .orElseThrow(() -> new NotFoundException("Skill", entry.skillId()));
            AssociateSkill rated = new AssociateSkill();
            rated.setAssociate(associate);
            rated.setSkill(skill);
            rated.setProficiency(entry.proficiency());
            rated.setPrimary(entry.primary());
            saved.add(associateSkillRepository.save(rated));
        }
        deriveHeadline(associate, saved);
        auditService.record("UPDATED", "Associate", id,
                "Updated skills of " + associate.getName() + " (" + request.skills().size() + " rated skills)");
        return get(id);
    }

    /**
     * Recomputes the associate's free-text headline (primarySkill/secondarySkill) from
     * their rated skills: the starred skill is primary (falling back to the highest
     * proficiency when none is starred); the next-highest proficiency is secondary.
     */
    private void deriveHeadline(Associate associate, List<AssociateSkill> skills) {
        AssociateSkill primary = skills.stream().filter(AssociateSkill::isPrimary).findFirst()
                .orElseGet(() -> skills.stream()
                        .max(Comparator.comparingInt(s -> s.getProficiency().ordinal())).orElse(null));
        AssociateSkill secondary = skills.stream()
                .filter(s -> primary == null || s != primary)
                .max(Comparator.comparingInt(s -> s.getProficiency().ordinal())).orElse(null);
        associate.setPrimarySkill(primary == null ? null : primary.getSkill().getName());
        associate.setSecondarySkill(secondary == null ? null : secondary.getSkill().getName());
        associateRepository.save(associate);
    }

    public AssociateResponse create(AssociateRequest request) {
        if (associateRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("An associate with email '" + request.email() + "' already exists");
        }
        if (request.employeeId() != null && !request.employeeId().isBlank()) {
            if (associateRepository.existsByEmployeeIdIgnoreCase(request.employeeId().trim())) {
                throw new ConflictException("An associate with employee ID '" + request.employeeId().trim() + "' already exists");
            }
        }
        Associate associate = new Associate();
        apply(associate, request);
        associate = associateRepository.save(associate);
        auditService.record("CREATED", "Associate", associate.getId(), "Created associate " + associate.getName()
                + (request.employmentHistory() == null || request.employmentHistory().isEmpty() ? ""
                        : " with " + request.employmentHistory().size()
                                + (request.employmentHistory().size() == 1
                                        ? " previous employer" : " previous employers")));
        if (request.skills() != null) {
            replaceSkills(associate.getId(), new SkillAssignmentRequest(request.skills()));
        }
        if (request.employmentHistory() != null) {
            int order = 0;
            for (EmploymentEntry entry : request.employmentHistory()) {
                EmploymentHistory row = new EmploymentHistory();
                row.setAssociateId(associate.getId());
                row.setCompany(entry.company());
                row.setTitle(entry.title());
                row.setStartDate(entry.startDate());
                row.setEndDate(entry.endDate());
                row.setSortOrder(order++);
                employmentHistoryRepository.save(row);
            }
        }
        exitInlineIfPast(associate);
        return get(associate.getId());
    }

    public AssociateResponse update(Long id, AssociateRequest request) {
        Associate associate = find(id);
        if (!associate.getEmail().equalsIgnoreCase(request.email())
                && associateRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("An associate with email '" + request.email() + "' already exists");
        }
        if (request.employeeId() != null && !request.employeeId().isBlank()) {
            boolean isNewOrChanged = associate.getEmployeeId() == null || !associate.getEmployeeId().equalsIgnoreCase(request.employeeId().trim());
            if (isNewOrChanged && associateRepository.existsByEmployeeIdIgnoreCase(request.employeeId().trim())) {
                throw new ConflictException("An associate with employee ID '" + request.employeeId().trim() + "' already exists");
            }
        }
        apply(associate, request);
        associateRepository.save(associate);
        auditService.record("UPDATED", "Associate", id, "Updated associate " + associate.getName());
        if (request.skills() != null) {
            replaceSkills(id, new SkillAssignmentRequest(request.skills()));
        }
        exitInlineIfPast(associate);
        // employmentHistory is deliberately NOT applied here — create-only per the
        // 2026-07-18 spec; post-create editing is a tracked follow-up.
        return get(id);
    }

    public void delete(Long id) {
        Associate associate = find(id);
        if (allocationRepository.existsByAssociateId(id)) {
            throw new ConflictException("Associate has allocations; remove them first");
        }
        associateRepository.delete(associate);
        auditService.record("DELETED", "Associate", id, "Deleted associate " + associate.getName());
    }

    private Associate find(Long id) {
        return associateRepository.findById(id).orElseThrow(() -> new NotFoundException("Associate", id));
    }

    /**
     * Applies exit cleanup for every ACTIVE associate whose last working day has
     * passed: status -> INACTIVE, open/later-ending allocations end on the last
     * working day, allocations that never started are removed. Idempotent — runs
     * daily from ExitScheduler and inline when an already-past exit is recorded.
     */
    @Transactional
    public void processExits() {
        java.time.LocalDate today = java.time.LocalDate.now();
        associateRepository.findAll().stream()
                .filter(a -> a.getStatus() == EntityStatus.ACTIVE)
                .filter(a -> a.getLastWorkingDay() != null && a.getLastWorkingDay().isBefore(today))
                .forEach(this::applyExit);
    }

    private void exitInlineIfPast(Associate associate) {
        if (associate.getLastWorkingDay() != null
                && associate.getLastWorkingDay().isBefore(java.time.LocalDate.now())
                && associate.getStatus() == EntityStatus.ACTIVE) {
            applyExit(associate);
        }
    }

    private void applyExit(Associate associate) {
        java.time.LocalDate lastDay = associate.getLastWorkingDay();
        int closed = 0;
        for (Allocation allocation : allocationRepository.findByAssociateId(associate.getId())) {
            if (allocation.getStartDate().isAfter(lastDay)) {
                allocationRepository.delete(allocation);
                closed++;
            } else if (allocation.getEndDate() == null || allocation.getEndDate().isAfter(lastDay)) {
                allocation.setEndDate(lastDay);
                closed++;
            }
        }
        associate.setStatus(EntityStatus.INACTIVE);
        auditService.record("EXITED", "Associate", associate.getId(),
                associate.getName() + " left on " + lastDay + " (" + associate.getExitReason() + "); "
                + closed + " allocation(s) closed");
    }

    private void apply(Associate associate, AssociateRequest request) {
        associate.setName(request.name());
        associate.setEmail(request.email());
        associate.setEmployeeId(request.employeeId() != null && !request.employeeId().isBlank() ? request.employeeId().trim() : null);
        associate.setCompany(request.company());
        associate.setLocation(request.location());
        associate.setWorkMode(request.workMode());
        associate.setDesignation(request.designation());
        associate.setPhone(request.phone());
        associate.setJoinedDate(request.joinedDate());
        if ((request.exitReason() == null) != (request.lastWorkingDay() == null)) {
            throw new BadRequestException("Exit reason and last working day must be provided together");
        }
        if (request.resignationDate() != null && request.lastWorkingDay() != null
                && request.resignationDate().isAfter(request.lastWorkingDay())) {
            throw new BadRequestException("Resignation date cannot be after the last working day");
        }
        associate.setResignationDate(request.resignationDate());
        associate.setLastWorkingDay(request.lastWorkingDay());
        associate.setExitReason(request.exitReason());
        // primarySkill/secondarySkill are no longer set here — they are derived from
        // the rated skills in deriveHeadline() whenever the skill set changes.
        associate.setStatus(request.status() == null ? EntityStatus.ACTIVE : request.status());
    }
}
