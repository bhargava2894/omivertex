package com.softility.omivertex.service;

import com.softility.omivertex.domain.Allocation;
import com.softility.omivertex.domain.Associate;
import com.softility.omivertex.domain.Project;
import com.softility.omivertex.repository.AllocationRepository;
import com.softility.omivertex.repository.AssociateRepository;
import com.softility.omivertex.repository.ProjectRepository;
import com.softility.omivertex.web.dto.AllocationRequest;
import com.softility.omivertex.web.dto.AllocationResponse;
import com.softility.omivertex.web.dto.AllocationUpdateRequest;
import com.softility.omivertex.web.error.ConflictException;
import com.softility.omivertex.web.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class AllocationService {

    private final AllocationRepository allocationRepository;
    private final AssociateRepository associateRepository;
    private final ProjectRepository projectRepository;

    public AllocationService(AllocationRepository allocationRepository,
                             AssociateRepository associateRepository,
                             ProjectRepository projectRepository) {
        this.allocationRepository = allocationRepository;
        this.associateRepository = associateRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional(readOnly = true)
    public List<AllocationResponse> list(Long projectId, Long associateId, Boolean active) {
        return allocationRepository.findAllWithDetails().stream()
                .filter(a -> projectId == null || a.getProject().getId().equals(projectId))
                .filter(a -> associateId == null || a.getAssociate().getId().equals(associateId))
                .filter(a -> active == null || a.isCurrent() == active)
                .map(AllocationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AllocationResponse get(Long id) {
        return AllocationResponse.from(find(id));
    }

    public AllocationResponse create(AllocationRequest request) {
        Associate associate = associateRepository.findById(request.associateId())
                .orElseThrow(() -> new NotFoundException("Associate", request.associateId()));
        Project project = projectRepository.findById(request.projectId())
                .orElseThrow(() -> new NotFoundException("Project", request.projectId()));
        if (allocationRepository.existsByAssociateIdAndProjectIdAndEndDateIsNull(
                request.associateId(), request.projectId())) {
            throw new ConflictException(associate.getName() + " already has an open allocation on " + project.getName());
        }
        Allocation allocation = new Allocation();
        allocation.setAssociate(associate);
        allocation.setProject(project);
        allocation.setBillable(request.billable());
        allocation.setAllocationPercent(request.allocationPercent() == null ? 100 : request.allocationPercent());
        allocation.setStartDate(request.startDate());
        allocation.setEndDate(request.endDate());
        return AllocationResponse.from(allocationRepository.save(allocation));
    }

    public AllocationResponse update(Long id, AllocationUpdateRequest request) {
        Allocation allocation = find(id);
        allocation.setBillable(request.billable());
        if (request.allocationPercent() != null) {
            allocation.setAllocationPercent(request.allocationPercent());
        }
        allocation.setStartDate(request.startDate());
        allocation.setEndDate(request.endDate());
        return AllocationResponse.from(allocation);
    }

    public void delete(Long id) {
        allocationRepository.delete(find(id));
    }

    private Allocation find(Long id) {
        return allocationRepository.findById(id).orElseThrow(() -> new NotFoundException("Allocation", id));
    }
}
