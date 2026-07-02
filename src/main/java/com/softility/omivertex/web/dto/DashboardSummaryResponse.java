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
        List<ClientHeadcount> clientHeadcounts,
        List<TrendPoint> staffingTrend) {

    public record ClientHeadcount(String clientName, long headcount) {
    }

    /** Distinct allocated / billable associates during one calendar month. */
    public record TrendPoint(String month, long total, long billable) {
    }
}
