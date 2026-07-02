package com.softility.omivertex.service;

import com.softility.omivertex.domain.Allocation;
import com.softility.omivertex.domain.Associate;
import com.softility.omivertex.domain.ProjectStatus;
import com.softility.omivertex.domain.WorkMode;
import com.softility.omivertex.repository.AllocationRepository;
import com.softility.omivertex.repository.AssociateRepository;
import com.softility.omivertex.repository.ClientRepository;
import com.softility.omivertex.repository.ProjectRepository;
import com.softility.omivertex.web.dto.DashboardSummaryResponse;
import com.softility.omivertex.web.dto.DashboardSummaryResponse.ClientHeadcount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final AssociateRepository associateRepository;
    private final ClientRepository clientRepository;
    private final ProjectRepository projectRepository;
    private final AllocationRepository allocationRepository;

    public DashboardService(AssociateRepository associateRepository, ClientRepository clientRepository,
                            ProjectRepository projectRepository, AllocationRepository allocationRepository) {
        this.associateRepository = associateRepository;
        this.clientRepository = clientRepository;
        this.projectRepository = projectRepository;
        this.allocationRepository = allocationRepository;
    }

    public DashboardSummaryResponse summary() {
        List<Associate> associates = associateRepository.findAll();
        List<Allocation> current = allocationRepository.findCurrent(LocalDate.now());

        Set<Long> billableIds = current.stream()
                .filter(Allocation::isBillable)
                .map(a -> a.getAssociate().getId())
                .collect(Collectors.toSet());
        Set<Long> allocatedIds = current.stream()
                .map(a -> a.getAssociate().getId())
                .collect(Collectors.toSet());

        long billableCount = billableIds.size();
        long nonBillableCount = allocatedIds.stream().filter(id -> !billableIds.contains(id)).count();
        long benchCount = associates.stream().filter(a -> !allocatedIds.contains(a.getId())).count();
        long onshore = associates.stream().filter(a -> a.getWorkMode() == WorkMode.ONSHORE).count();
        long offshore = associates.stream().filter(a -> a.getWorkMode() == WorkMode.OFFSHORE).count();

        Map<String, Set<Long>> byClient = current.stream().collect(Collectors.groupingBy(
                a -> a.getProject().getClient().getName(),
                Collectors.mapping(a -> a.getAssociate().getId(), Collectors.toSet())));
        List<ClientHeadcount> headcounts = byClient.entrySet().stream()
                .map(e -> new ClientHeadcount(e.getKey(), e.getValue().size()))
                .sorted(Comparator.comparingLong(ClientHeadcount::headcount).reversed()
                        .thenComparing(ClientHeadcount::clientName))
                .toList();

        return new DashboardSummaryResponse(associates.size(), billableCount, nonBillableCount, benchCount,
                onshore, offshore, clientRepository.count(),
                projectRepository.findAll().stream().filter(p -> p.getStatus() == ProjectStatus.ACTIVE).count(),
                headcounts);
    }
}
