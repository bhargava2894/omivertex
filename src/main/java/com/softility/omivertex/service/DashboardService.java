package com.softility.omivertex.service;

import com.softility.omivertex.domain.Allocation;
import com.softility.omivertex.domain.Associate;
import com.softility.omivertex.domain.EntityStatus;
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

    /** Bench-aging bucket boundaries (days). The dashboard UI mirrors these tones. */
    static final int BENCH_FRESH_MAX_DAYS = 30;
    static final int BENCH_WARN_MAX_DAYS = 60;
    /** How far ahead the roll-off radar looks for ending allocations (days). */
    static final int ROLLOFF_HORIZON_DAYS = 30;
    /** How far ahead the certification-expiry radar looks (days). */
    static final int CERT_EXPIRY_HORIZON_DAYS = 90;
    /** Trailing window for the exits (attrition) KPI (days). */
    static final int EXIT_WINDOW_DAYS = 365;
    /** Horizons for the deterministic utilization forecast (days from today). */
    static final int[] FORECAST_OFFSET_DAYS = {0, 30, 60, 90};
    /** Named drivers listed per horizon before the panel falls back to "…and N more". */
    static final int MAX_FORECAST_DRIVERS = 5;

    private final AssociateRepository associateRepository;
    private final ClientRepository clientRepository;
    private final ProjectRepository projectRepository;
    private final AllocationRepository allocationRepository;
    private final com.softility.omivertex.repository.OpenPositionRepository openPositionRepository;
    private final com.softility.omivertex.repository.CertificationRepository certificationRepository;
    private final SkillGapService skillGapService;

    public DashboardService(AssociateRepository associateRepository, ClientRepository clientRepository,
                            ProjectRepository projectRepository, AllocationRepository allocationRepository,
                            com.softility.omivertex.repository.OpenPositionRepository openPositionRepository,
                            com.softility.omivertex.repository.CertificationRepository certificationRepository,
                            SkillGapService skillGapService) {
        this.associateRepository = associateRepository;
        this.clientRepository = clientRepository;
        this.projectRepository = projectRepository;
        this.allocationRepository = allocationRepository;
        this.openPositionRepository = openPositionRepository;
        this.certificationRepository = certificationRepository;
        this.skillGapService = skillGapService;
    }

    /**
     * Certifications expiring within {@code withinDays} of today, soonest first.
     * Upcoming only — already-expired certs are excluded. Both endpoints inclusive: a
     * cert expiring today or exactly {@code withinDays} days out is included; certs
     * with no expiry date are skipped. Shared by the dashboard radar (fixed
     * {@link #CERT_EXPIRY_HORIZON_DAYS}) and the assistant tool (caller-chosen
     * window), so the filter exists exactly once.
     */
    public List<DashboardSummaryResponse.ExpiringCert> expiringCerts(int withinDays) {
        LocalDate today = LocalDate.now();
        LocalDate limit = today.plusDays(withinDays);
        return certificationRepository.findAllWithAssociate().stream()
                .filter(c -> c.getExpiryDate() != null
                        && !c.getExpiryDate().isBefore(today)
                        && !c.getExpiryDate().isAfter(limit))
                .sorted(Comparator.comparing(com.softility.omivertex.domain.Certification::getExpiryDate))
                .map(c -> new DashboardSummaryResponse.ExpiringCert(c.getId(),
                        c.getAssociate().getId(), c.getAssociate().getName(),
                        c.getName(), c.getExpiryDate(), ChronoUnit.DAYS.between(today, c.getExpiryDate())))
                .toList();
    }

    public DashboardSummaryResponse summary() {
        // Every KPI is about the active workforce: leavers (INACTIVE) must not
        // inflate the bench or dilute utilization, even with lingering allocations.
        List<Associate> associates = associateRepository.findAll().stream()
                .filter(a -> a.getStatus() == EntityStatus.ACTIVE)
                .toList();
        Set<Long> activeIds = associates.stream().map(Associate::getId).collect(Collectors.toSet());
        List<Allocation> all = allocationRepository.findAllWithDetails();
        List<Allocation> current = all.stream()
                .filter(Allocation::isCurrent)
                .filter(a -> activeIds.contains(a.getAssociate().getId()))
                .toList();
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

        // Distinct associates per client, split billable/non-billable. Billable wins:
        // one billable allocation under the client makes the person billable there.
        record ClientKey(Long id, String name) {}
        Map<ClientKey, List<Allocation>> byClient = current.stream().collect(Collectors.groupingBy(
                a -> new ClientKey(a.getProject().getClient().getId(), a.getProject().getClient().getName())));
        List<ClientHeadcount> headcounts = byClient.entrySet().stream()
                .map(e -> {
                    Set<Long> billableHere = e.getValue().stream()
                            .filter(Allocation::isBillable)
                            .map(a -> a.getAssociate().getId())
                            .collect(Collectors.toSet());
                    Set<Long> everyone = e.getValue().stream()
                            .map(a -> a.getAssociate().getId())
                            .collect(Collectors.toSet());
                    long nonBillable = everyone.stream().filter(id -> !billableHere.contains(id)).count();
                    return new ClientHeadcount(e.getKey().id(), e.getKey().name(),
                            everyone.size(), billableHere.size(), nonBillable);
                })
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
                benchAssociates.stream().filter(b -> b.benchDays() <= BENCH_FRESH_MAX_DAYS).count(),
                benchAssociates.stream().filter(b -> b.benchDays() > BENCH_FRESH_MAX_DAYS && b.benchDays() <= BENCH_WARN_MAX_DAYS).count(),
                benchAssociates.stream().filter(b -> b.benchDays() > BENCH_WARN_MAX_DAYS).count());

        // roll-off radar: current allocations ending within the horizon
        LocalDate today = LocalDate.now();
        List<DashboardSummaryResponse.Rolloff> rolloffs = current.stream()
                .filter(a -> a.getEndDate() != null && !a.getEndDate().isAfter(today.plusDays(ROLLOFF_HORIZON_DAYS)))
                .sorted(Comparator.comparing(Allocation::getEndDate))
                .map(a -> new DashboardSummaryResponse.Rolloff(a.getId(),
                        a.getAssociate().getId(), a.getAssociate().getName(),
                        a.getProject().getName(), a.getProject().getClient().getName(),
                        a.getEndDate(), ChronoUnit.DAYS.between(today, a.getEndDate())))
                .toList();

        // expiring certifications: shared filter lives in expiringCerts(int)
        List<DashboardSummaryResponse.ExpiringCert> expiringCerts = expiringCerts(CERT_EXPIRY_HORIZON_DAYS);

        // skill gaps: shared math lives in SkillGapService (the full report reuses it)
        List<DashboardSummaryResponse.SkillGap> skillGaps = skillGapService.dashboardPanel();

        // exits KPI counts leavers, who are INACTIVE — use the unfiltered roster
        LocalDate exitWindowStart = today.minusDays(EXIT_WINDOW_DAYS);
        long exitsLast12Months = associateRepository.findAll().stream()
                .filter(a -> a.getLastWorkingDay() != null
                        && !a.getLastWorkingDay().isAfter(today)
                        && !a.getLastWorkingDay().isBefore(exitWindowStart))
                .count();

        return new DashboardSummaryResponse(associates.size(), billableCount, nonBillableCount, benchCount,
                onshore, offshore, clientRepository.count(),
                projectRepository.findAll().stream().filter(p -> p.getStatus() == ProjectStatus.ACTIVE).count(),
                openPositionRepository.countByStatus(PositionStatus.OPEN),
                utilization, benchAging, benchAssociates, rolloffs,
                headcounts, staffingTrend(), expiringCerts, exitsLast12Months, skillGaps,
                utilizationForecast(associates, all));
    }

    /**
     * FTE-weighted utilization evaluated as of each forecast horizon, using only
     * known allocation windows and recorded exits — no new assignments assumed.
     * Each horizon also reports the events that moved it away from today's number,
     * because the bare percentages cannot distinguish "flat: nothing is scheduled"
     * from "flat: a roll-off and an exit are cancelling each other out".
     */
    private List<DashboardSummaryResponse.ForecastPoint> utilizationForecast(
            List<Associate> activeAssociates, List<Allocation> all) {
        LocalDate today = LocalDate.now();
        Map<Long, Integer> billableToday = billablePercentAt(all, presentIds(activeAssociates, today), today);
        long todayPct = percentAt(activeAssociates, all, today);

        List<DashboardSummaryResponse.ForecastPoint> points = new ArrayList<>();
        for (int offset : FORECAST_OFFSET_DAYS) {
            LocalDate at = today.plusDays(offset);
            long pct = percentAt(activeAssociates, all, at);
            List<DashboardSummaryResponse.ForecastDriver> drivers = offset == 0 ? List.of()
                    : forecastDrivers(activeAssociates, all, billableToday, today, at);
            points.add(new DashboardSummaryResponse.ForecastPoint(
                    offset == 0 ? "Today" : "+" + offset + "d", pct, pct - todayPct,
                    drivers.stream().limit(MAX_FORECAST_DRIVERS).toList(),
                    Math.max(0, drivers.size() - MAX_FORECAST_DRIVERS)));
        }
        return points;
    }

    /**
     * The scheduled events between {@code today} and {@code at} that move utilization.
     * An associate who exits in the window is reported once, as an exit — their
     * allocations ending with them are not also counted as roll-offs.
     */
    private List<DashboardSummaryResponse.ForecastDriver> forecastDrivers(
            List<Associate> activeAssociates, List<Allocation> all,
            Map<Long, Integer> billableToday, LocalDate today, LocalDate at) {
        Set<Long> presentAtHorizon = presentIds(activeAssociates, at);
        List<DashboardSummaryResponse.ForecastDriver> drivers = new ArrayList<>();

        for (Associate a : activeAssociates) {
            LocalDate lwd = a.getLastWorkingDay();
            boolean leavesInWindow = lwd != null && !lwd.isBefore(today) && lwd.isBefore(at);
            if (leavesInWindow) {
                // A benched leaver LEAVES THE DENOMINATOR ONLY, so utilization goes UP.
                boolean billable = billableToday.getOrDefault(a.getId(), 0) > 0;
                drivers.add(new DashboardSummaryResponse.ForecastDriver(
                        billable ? DashboardSummaryResponse.DriverKind.BILLABLE_EXIT
                                : DashboardSummaryResponse.DriverKind.BENCH_EXIT,
                        a.getId(), a.getName(), null, lwd));
            }
        }

        for (Allocation al : all) {
            if (!al.isBillable() || !presentAtHorizon.contains(al.getAssociate().getId())) {
                continue; // a leaver's allocations are already told as the exit
            }
            boolean liveToday = !al.getStartDate().isAfter(today)
                    && (al.getEndDate() == null || !al.getEndDate().isBefore(today));
            boolean liveAtHorizon = !al.getStartDate().isAfter(at)
                    && (al.getEndDate() == null || !al.getEndDate().isBefore(at));
            if (liveToday && !liveAtHorizon) {
                drivers.add(new DashboardSummaryResponse.ForecastDriver(
                        DashboardSummaryResponse.DriverKind.ROLL_OFF,
                        al.getAssociate().getId(), al.getAssociate().getName(),
                        al.getProject().getName(), al.getEndDate()));
            } else if (!liveToday && liveAtHorizon) {
                drivers.add(new DashboardSummaryResponse.ForecastDriver(
                        DashboardSummaryResponse.DriverKind.RAMP_UP,
                        al.getAssociate().getId(), al.getAssociate().getName(),
                        al.getProject().getName(), al.getStartDate()));
            }
        }
        drivers.sort(Comparator.comparing(DashboardSummaryResponse.ForecastDriver::date));
        return drivers;
    }

    /** FTE-weighted billable utilization as of {@code at}, over the people still present then. */
    private long percentAt(List<Associate> activeAssociates, List<Allocation> all, LocalDate at) {
        Set<Long> present = presentIds(activeAssociates, at);
        double fte = billablePercentAt(all, present, at).values().stream()
                .mapToDouble(p -> Math.min(p, 100) / 100.0).sum();
        return present.isEmpty() ? 0 : Math.round(fte / present.size() * 100);
    }

    private Set<Long> presentIds(List<Associate> activeAssociates, LocalDate at) {
        return activeAssociates.stream()
                .filter(a -> a.getLastWorkingDay() == null || !a.getLastWorkingDay().isBefore(at))
                .map(Associate::getId).collect(Collectors.toSet());
    }

    private Map<Long, Integer> billablePercentAt(List<Allocation> all, Set<Long> present, LocalDate at) {
        return all.stream()
                .filter(Allocation::isBillable)
                .filter(a -> !a.getStartDate().isAfter(at)
                        && (a.getEndDate() == null || !a.getEndDate().isBefore(at)))
                .filter(a -> present.contains(a.getAssociate().getId()))
                .collect(Collectors.groupingBy(a -> a.getAssociate().getId(),
                        Collectors.summingInt(Allocation::getAllocationPercent)));
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
