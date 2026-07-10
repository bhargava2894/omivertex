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
        List<SkillGap> skillGaps) {

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
}
