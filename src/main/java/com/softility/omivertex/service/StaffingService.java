package com.softility.omivertex.service;

import com.softility.omivertex.domain.Allocation;
import com.softility.omivertex.repository.AllocationRepository;
import com.softility.omivertex.web.dto.StaffingDtos.StaffedAssociate;
import com.softility.omivertex.web.dto.StaffingDtos.StaffedClient;
import com.softility.omivertex.web.dto.StaffingDtos.StaffedProject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds the company → project → associates staffing tree from CURRENT allocations.
 * Client-level billable counting matches the dashboard rule: one billable allocation
 * under the client makes the person billable there; each person counts once per client.
 */
@Service
@Transactional(readOnly = true)
public class StaffingService {

    private final AllocationRepository allocationRepository;

    public StaffingService(AllocationRepository allocationRepository) {
        this.allocationRepository = allocationRepository;
    }

    public List<StaffedClient> staffing() {
        List<Allocation> current = allocationRepository.findAllWithDetails().stream()
                .filter(Allocation::isCurrent)
                .toList();

        Map<Long, List<Allocation>> byClient = current.stream()
                .collect(Collectors.groupingBy(a -> a.getProject().getClient().getId()));

        return byClient.values().stream()
                .map(this::toClient)
                .sorted(Comparator.comparingLong(
                                (StaffedClient c) -> c.billable() + c.nonBillable()).reversed()
                        .thenComparing(StaffedClient::clientName))
                .toList();
    }

    private StaffedClient toClient(List<Allocation> clientAllocations) {
        var client = clientAllocations.get(0).getProject().getClient();
        Set<Long> billableHere = clientAllocations.stream()
                .filter(Allocation::isBillable)
                .map(a -> a.getAssociate().getId())
                .collect(Collectors.toSet());
        long everyone = clientAllocations.stream()
                .map(a -> a.getAssociate().getId()).distinct().count();

        List<StaffedProject> projects = clientAllocations.stream()
                .collect(Collectors.groupingBy(a -> a.getProject().getId()))
                .values().stream()
                .map(this::toProject)
                .sorted(Comparator.comparing(StaffedProject::projectName))
                .toList();

        return new StaffedClient(client.getId(), client.getName(),
                billableHere.size(), everyone - billableHere.size(), projects);
    }

    private StaffedProject toProject(List<Allocation> projectAllocations) {
        var project = projectAllocations.get(0).getProject();
        List<StaffedAssociate> associates = projectAllocations.stream()
                .map(a -> new StaffedAssociate(a.getAssociate().getId(), a.getAssociate().getName(),
                        a.getAssociate().getDesignation(), a.getAllocationPercent(),
                        a.isBillable(), a.getStartDate()))
                .sorted(Comparator.comparing(StaffedAssociate::name))
                .toList();
        long billable = associates.stream().filter(StaffedAssociate::billable)
                .map(StaffedAssociate::associateId).distinct().count();
        long total = associates.stream().map(StaffedAssociate::associateId).distinct().count();
        return new StaffedProject(project.getId(), project.getName(), project.getCode(),
                billable, total - billable, associates);
    }
}
