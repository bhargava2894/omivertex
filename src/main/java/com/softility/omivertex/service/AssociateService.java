package com.softility.omivertex.service;

import com.softility.omivertex.domain.Allocation;
import com.softility.omivertex.domain.Associate;
import com.softility.omivertex.domain.AssociateSkill;
import com.softility.omivertex.domain.Skill;
import com.softility.omivertex.domain.EntityStatus;
import com.softility.omivertex.domain.WorkMode;
import com.softility.omivertex.domain.Proficiency;
import com.softility.omivertex.repository.AllocationRepository;
import com.softility.omivertex.repository.AssociateRepository;
import com.softility.omivertex.repository.AssociateSkillRepository;
import com.softility.omivertex.repository.SkillRepository;
import com.softility.omivertex.web.dto.AssociateRequest;
import com.softility.omivertex.web.dto.AssociateResponse;
import com.softility.omivertex.web.dto.SkillAssignmentRequest;
import com.softility.omivertex.web.error.ConflictException;
import com.softility.omivertex.web.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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

    public AssociateService(AssociateRepository associateRepository, AllocationRepository allocationRepository,
                            AuditService auditService, AssociateSkillRepository associateSkillRepository,
                            SkillRepository skillRepository) {
        this.associateRepository = associateRepository;
        this.allocationRepository = allocationRepository;
        this.auditService = auditService;
        this.associateSkillRepository = associateSkillRepository;
        this.skillRepository = skillRepository;
    }

    @Transactional(readOnly = true)
    public List<AssociateResponse> list(WorkMode workMode, Boolean billable, Boolean bench,
                                        Long categoryId, Long skillId, Proficiency minProficiency) {
        Map<Long, List<Allocation>> allocationsByAssociate = allocationRepository.findAllWithDetails().stream()
                .collect(Collectors.groupingBy(a -> a.getAssociate().getId()));
        Map<Long, List<AssociateSkill>> skillsByAssociate = associateSkillRepository.findAllWithDetails().stream()
                .collect(Collectors.groupingBy(s -> s.getAssociate().getId()));
        return associateRepository.findAll().stream()
                .map(associate -> AssociateResponse.from(associate,
                        allocationsByAssociate.getOrDefault(associate.getId(), List.of()),
                        skillsByAssociate.getOrDefault(associate.getId(), List.of())))
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
        return AssociateResponse.from(associate, allocationRepository.findByAssociateId(id),
                associateSkillRepository.findByAssociateId(id));
    }

    /** Replaces the associate's entire rated-skill set with the given entries. */
    public AssociateResponse replaceSkills(Long id, SkillAssignmentRequest request) {
        Associate associate = find(id);
        associateSkillRepository.deleteByAssociateId(id);
        associateSkillRepository.flush(); // deletes must hit the DB before re-inserts touch the unique constraint
        for (SkillAssignmentRequest.Entry entry : request.skills()) {
            Skill skill = skillRepository.findById(entry.skillId())
                    .orElseThrow(() -> new NotFoundException("Skill", entry.skillId()));
            AssociateSkill rated = new AssociateSkill();
            rated.setAssociate(associate);
            rated.setSkill(skill);
            rated.setProficiency(entry.proficiency());
            associateSkillRepository.save(rated);
        }
        auditService.record("UPDATED", "Associate", id,
                "Updated skills of " + associate.getName() + " (" + request.skills().size() + " rated skills)");
        return get(id);
    }

    public AssociateResponse create(AssociateRequest request) {
        if (associateRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("An associate with email '" + request.email() + "' already exists");
        }
        Associate associate = new Associate();
        apply(associate, request);
        associate = associateRepository.save(associate);
        auditService.record("CREATED", "Associate", associate.getId(), "Created associate " + associate.getName());
        return AssociateResponse.from(associate, List.of(), List.of());
    }

    public AssociateResponse update(Long id, AssociateRequest request) {
        Associate associate = find(id);
        if (!associate.getEmail().equalsIgnoreCase(request.email())
                && associateRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("An associate with email '" + request.email() + "' already exists");
        }
        apply(associate, request);
        associateRepository.save(associate);
        auditService.record("UPDATED", "Associate", id, "Updated associate " + associate.getName());
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

    private void apply(Associate associate, AssociateRequest request) {
        associate.setName(request.name());
        associate.setEmail(request.email());
        associate.setCompany(request.company());
        associate.setLocation(request.location());
        associate.setWorkMode(request.workMode());
        associate.setDesignation(request.designation());
        associate.setPrimarySkill(request.primarySkill());
        associate.setSecondarySkill(request.secondarySkill());
        associate.setStatus(request.status() == null ? EntityStatus.ACTIVE : request.status());
    }
}
