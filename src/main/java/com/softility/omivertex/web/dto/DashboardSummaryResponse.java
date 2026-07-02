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
        List<ClientHeadcount> clientHeadcounts) {

    public record ClientHeadcount(String clientName, long headcount) {
    }
}
