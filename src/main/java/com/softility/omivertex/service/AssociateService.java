package com.softility.omivertex.service;

import com.softility.omivertex.domain.Allocation;
import com.softility.omivertex.domain.Associate;
import com.softility.omivertex.domain.EntityStatus;
import com.softility.omivertex.domain.WorkMode;
import com.softility.omivertex.repository.AllocationRepository;
import com.softility.omivertex.repository.AssociateRepository;
import com.softility.omivertex.web.dto.AssociateRequest;
import com.softility.omivertex.web.dto.AssociateResponse;
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

    public AssociateService(AssociateRepository associateRepository, AllocationRepository allocationRepository) {
        this.associateRepository = associateRepository;
        this.allocationRepository = allocationRepository;
    }

    @Transactional(readOnly = true)
    public List<AssociateResponse> list(WorkMode workMode, Boolean billable, Boolean bench) {
        Map<Long, List<Allocation>> allocationsByAssociate = allocationRepository.findAllWithDetails().stream()
                .collect(Collectors.groupingBy(a -> a.getAssociate().getId()));
        return associateRepository.findAll().stream()
                .map(associate -> AssociateResponse.from(associate,
                        allocationsByAssociate.getOrDefault(associate.getId(), List.of())))
                .filter(r -> workMode == null || r.workMode() == workMode)
                .filter(r -> billable == null || r.billable() == billable)
                .filter(r -> bench == null || (r.currentProjectId() == null) == bench)
                .toList();
    }

    @Transactional(readOnly = true)
    public AssociateResponse get(Long id) {
        Associate associate = find(id);
        return AssociateResponse.from(associate, allocationRepository.findByAssociateId(id));
    }

    public AssociateResponse create(AssociateRequest request) {
        if (associateRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("An associate with email '" + request.email() + "' already exists");
        }
        Associate associate = new Associate();
        apply(associate, request);
        return AssociateResponse.from(associateRepository.save(associate), List.of());
    }

    public AssociateResponse update(Long id, AssociateRequest request) {
        Associate associate = find(id);
        if (!associate.getEmail().equalsIgnoreCase(request.email())
                && associateRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("An associate with email '" + request.email() + "' already exists");
        }
        apply(associate, request);
        associateRepository.save(associate);
        return get(id);
    }

    public void delete(Long id) {
        Associate associate = find(id);
        if (allocationRepository.existsByAssociateId(id)) {
            throw new ConflictException("Associate has allocations; remove them first");
        }
        associateRepository.delete(associate);
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
