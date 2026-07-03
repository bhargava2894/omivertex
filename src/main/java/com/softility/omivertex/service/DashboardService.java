package com.softility.omivertex.service;

import com.softility.omivertex.domain.Allocation;
import com.softility.omivertex.domain.Associate;
import com.softility.omivertex.domain.PositionStatus;
import com.softility.omivertex.domain.ProjectStatus;
import com.softility.omivertex.domain.WorkMode;
import com.softility.omivertex.repository.AllocationRepository;
import com.softility.omivertex.repository.AssociateRepository;
import com.softility.omivertex.repository.ClientRepository;
import com.softility.omivertex.repository.ProjectRepository;
import com.softility.omivertex.web.dto.AssociateResponse;
import com.softility.omivertex.web.dto.DashboardSummaryResponse;
import com.softility.omivertex.web.dto.DashboardSummaryResponse.ClientHeadcount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final AssociateRepository associateRepository;
    private final ClientRepository clientRepository;
    private final ProjectRepository projectRepository;
    private final AllocationRepository allocationRepository;
    private final com.softility.omivertex.repository.OpenPositionRepository openPositionRepository;
    private final com.softility.omivertex.repository.CertificationRepository certificationRepository;

    public DashboardService(AssociateRepository associateRepository, ClientRepository clientRepository,
                            ProjectRepository projectRepository, AllocationRepository allocationRepository,
                            com.softility.omivertex.repository.OpenPositionRepository openPositionRepository,
                            com.softility.omivertex.repository.CertificationRepository certificationRepository) {
        this.associateRepository = associateRepository;
        this.clientRepository = clientRepository;
        this.projectRepository = projectRepository;
        this.allocationRepository = allocationRepository;
        this.openPositionRepository = openPositionRepository;
        this.certificationRepository = certificationRepository;
    }

    public DashboardSummaryResponse summary() {
        List<Associate> associates = associateRepository.findAll();
        List<Allocation> all = allocationRepository.findAllWithDetails();
        List<Allocation> current = all.stream().filter(Allocation::isCurrent).toList();
        Map<Long, List<Allocation>> byAssociate = all.stream()
                .collect(Collectors.groupingBy(a -> a.getAssociate().getId()));

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

        // FTE-weighted utilization: each associate contributes their current billable
        // allocation percentage, capped at 100
        Map<Long, Integer> billablePctByAssociate = current.stream()
                .filter(Allocation::isBillable)
                .collect(Collectors.groupingBy(a -> a.getAssociate().getId(),
                        Collectors.summingInt(Allocation::getAllocationPercent)));
        double billableFte = billablePctByAssociate.values().stream()
                .mapToDouble(pct -> Math.min(pct, 100) / 100.0).sum();
        long utilization = associates.isEmpty() ? 0 : Math.round(billableFte / associates.size() * 100);

        // bench aging
        List<DashboardSummaryResponse.BenchAssociate> benchAssociates = associates.stream()
                .map(a -> {
                    Long days = AssociateResponse.benchDays(a, byAssociate.getOrDefault(a.getId(), List.of()));
                    return days == null ? null
                            : new DashboardSummaryResponse.BenchAssociate(a.getId(), a.getName(), a.getDesignation(), days);
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingLong(DashboardSummaryResponse.BenchAssociate::benchDays).reversed())
                .toList();
        DashboardSummaryResponse.BenchAging benchAging = new DashboardSummaryResponse.BenchAging(
                benchAssociates.stream().filter(b -> b.benchDays() <= 30).count(),
                benchAssociates.stream().filter(b -> b.benchDays() > 30 && b.benchDays() <= 60).count(),
                benchAssociates.stream().filter(b -> b.benchDays() > 60).count());

        // roll-off radar: current allocations ending within 30 days
        LocalDate today = LocalDate.now();
        List<DashboardSummaryResponse.Rolloff> rolloffs = current.stream()
                .filter(a -> a.getEndDate() != null && !a.getEndDate().isAfter(today.plusDays(30)))
                .sorted(Comparator.comparing(Allocation::getEndDate))
                .map(a -> new DashboardSummaryResponse.Rolloff(a.getId(),
                        a.getAssociate().getId(), a.getAssociate().getName(),
                        a.getProject().getName(), a.getProject().getClient().getName(),
                        a.getEndDate(), ChronoUnit.DAYS.between(today, a.getEndDate())))
                .toList();

        // expiring certifications: certifications expiring within 90 days
        LocalDate certExpiryLimit = today.plusDays(90);
        List<DashboardSummaryResponse.ExpiringCert> expiringCerts = certificationRepository.findAllWithAssociate().stream()
                .filter(c -> c.getExpiryDate() != null
                        && !c.getExpiryDate().isBefore(today)
                        && !c.getExpiryDate().isAfter(certExpiryLimit))
                .sorted(Comparator.comparing(com.softility.omivertex.domain.Certification::getExpiryDate))
                .map(c -> new DashboardSummaryResponse.ExpiringCert(c.getId(),
                        c.getAssociate().getId(), c.getAssociate().getName(),
                        c.getName(), c.getExpiryDate(), ChronoUnit.DAYS.between(today, c.getExpiryDate())))
                .toList();

        return new DashboardSummaryResponse(associates.size(), billableCount, nonBillableCount, benchCount,
                onshore, offshore, clientRepository.count(),
                projectRepository.findAll().stream().filter(p -> p.getStatus() == ProjectStatus.ACTIVE).count(),
                openPositionRepository.countByStatus(PositionStatus.OPEN),
                utilization, benchAging, benchAssociates, rolloffs,
                headcounts, staffingTrend(), expiringCerts);
    }

    /** Distinct allocated / billable associates per month for the trailing six months. */
    private List<DashboardSummaryResponse.TrendPoint> staffingTrend() {
        List<Allocation> all = allocationRepository.findAllWithDetails();
        DateTimeFormatter monthLabel = DateTimeFormatter.ofPattern("MMM");
        List<DashboardSummaryResponse.TrendPoint> points = new ArrayList<>();
        YearMonth current = YearMonth.now();
        for (int i = 5; i >= 0; i--) {
            YearMonth month = current.minusMonths(i);
            LocalDate start = month.atDay(1);
            LocalDate end = month.atEndOfMonth();
            List<Allocation> active = all.stream()
                    .filter(a -> !a.getStartDate().isAfter(end)
                            && (a.getEndDate() == null || !a.getEndDate().isBefore(start)))
                    .toList();
            long total = active.stream().map(a -> a.getAssociate().getId()).distinct().count();
            long billable = active.stream().filter(Allocation::isBillable)
                    .map(a -> a.getAssociate().getId()).distinct().count();
            points.add(new DashboardSummaryResponse.TrendPoint(monthLabel.format(start), total, billable));
        }
        return points;
    }
}
