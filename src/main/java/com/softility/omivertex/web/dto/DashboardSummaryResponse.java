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
        long utilizationPercent,
        BenchAging benchAging,
        List<BenchAssociate> benchAssociates,
        List<Rolloff> upcomingRolloffs,
        List<ClientHeadcount> clientHeadcounts,
        List<TrendPoint> staffingTrend) {

    public record ClientHeadcount(String clientName, long headcount) {
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
}
