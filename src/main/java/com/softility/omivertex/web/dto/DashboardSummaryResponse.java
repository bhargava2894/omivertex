package com.softility.omivertex.web.dto;

import java.util.List;

public record DashboardSummaryResponse(
        long totalAssociates,
        long billableCount,
        long nonBillableCount,
        long benchCount,
        long onshoreCount,
        long offshoreCount,
        long totalClients,
        long activeProjects,
        long openPositions,
        long utilizationPercent,
        BenchAging benchAging,
        List<BenchAssociate> benchAssociates,
        List<Rolloff> upcomingRolloffs,
        List<ClientHeadcount> clientHeadcounts,
        List<TrendPoint> staffingTrend,
        List<ExpiringCert> expiringCertifications,
        long exitsLast12Months,
        List<SkillGap> skillGaps,
        List<ForecastPoint> utilizationForecast) {

    /**
     * Distinct current associates per client, split by billing. An associate counts
     * as billable for a client when ANY of their current allocations there is
     * billable; headcount == billable + nonBillable.
     */
    public record ClientHeadcount(Long clientId, String clientName, long headcount,
                                  long billable, long nonBillable) {
    }

    /** Distinct allocated / billable associates during one calendar month. */
    public record TrendPoint(String month, long total, long billable) {
    }

    /** Bench population bucketed by how long they have been unallocated. */
    public record BenchAging(long days0to30, long days31to60, long days60plus) {
    }

    public record BenchAssociate(Long id, String name, String designation, long benchDays) {
    }

    /** A current allocation ending within the next 30 days. */
    public record Rolloff(Long allocationId, Long associateId, String associateName,
                          String projectName, String clientName,
                          java.time.LocalDate endDate, long daysLeft) {
    }

    /** A certification expiring within the next 90 days. */
    public record ExpiringCert(Long certificationId, Long associateId, String associateName,
                               String name, java.time.LocalDate expiryDate, long daysLeft) {
    }

    /**
     * Demand vs supply for one skill required by open positions. Supply counts
     * ACTIVE associates at or above the lowest demanded proficiency; gap =
     * demand - benchSupply (positive = hire or train).
     */
    public record SkillGap(Long skillId, String skillName, String category,
                           long demand, long benchSupply, long totalSupply, long gap) {
    }

    /**
     * FTE-weighted utilization projected at a future date from known allocation
     * end dates and recorded exits — deterministic, assumes no new assignments.
     *
     * <p>{@code deltaPoints} is the change in percentage points from TODAY (not from the
     * previous horizon), and {@code drivers} are the events between today and this horizon
     * that caused it. Both are measured against today so each row stands alone.
     *
     * <p>Drivers are named but never individually scored: utilization is a ratio, so when
     * an exit moves the denominator the per-driver effects genuinely do not sum to
     * {@code deltaPoints}. Publishing a per-driver point value would be arithmetic that
     * does not add up.
     */
    public record ForecastPoint(String label, long percent, long deltaPoints,
                                List<ForecastDriver> drivers, long omittedDrivers) {
    }

    /**
     * One scheduled event that moves utilization between today and a forecast horizon.
     * A {@code BENCH_EXIT} <em>raises</em> utilization — the leaver drops out of the
     * denominator while billable FTE is unchanged — which is the opposite of what most
     * readers assume, hence the separate kind.
     */
    public record ForecastDriver(DriverKind kind, Long associateId, String associateName,
                                 String projectName, java.time.LocalDate date) {
    }

    /** Why a forecast number moved. Direction is a property of the kind. */
    public enum DriverKind {
        /** A billable allocation live today has ended by the horizon. Lowers utilization. */
        ROLL_OFF,
        /** An allocation starts between today and the horizon. Raises utilization. */
        RAMP_UP,
        /** A benched associate leaves: denominator shrinks. Raises utilization. */
        BENCH_EXIT,
        /** A billable associate leaves: billable FTE goes with them. Lowers utilization. */
        BILLABLE_EXIT
    }
}
