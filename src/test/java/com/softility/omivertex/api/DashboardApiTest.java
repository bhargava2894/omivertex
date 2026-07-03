package com.softility.omivertex.api;

import com.softility.omivertex.domain.WorkMode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DashboardApiTest extends ApiTestBase {

    @Test
    void summary_returnsResourceKpis() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var billableDev = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        var shadowDev = associate("Rahul Verma", "rahul@softility.com", WorkMode.ONSHORE);
        associate("Anita Rao", "anita@softility.com", WorkMode.ONSHORE);
        allocation(billableDev, proj, true);
        var rollingOff = allocation(shadowDev, proj, false);
        rollingOff.setEndDate(LocalDate.now().plusDays(10));
        allocationRepository.save(rollingOff);

        mockMvc.perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAssociates").value(3))
                .andExpect(jsonPath("$.billableCount").value(1))
                .andExpect(jsonPath("$.nonBillableCount").value(1))
                .andExpect(jsonPath("$.benchCount").value(1))
                .andExpect(jsonPath("$.onshoreCount").value(2))
                .andExpect(jsonPath("$.offshoreCount").value(1))
                .andExpect(jsonPath("$.totalClients").value(1))
                .andExpect(jsonPath("$.activeProjects").value(1))
                .andExpect(jsonPath("$.clientHeadcounts", hasSize(1)))
                .andExpect(jsonPath("$.clientHeadcounts[0].clientName").value("Acme Corp"))
                .andExpect(jsonPath("$.clientHeadcounts[0].headcount").value(2))
                // allocations in ApiTestBase start 3 months ago, so the last 4 points
                // of the 6-month trend carry them and the first 2 are empty
                .andExpect(jsonPath("$.staffingTrend", hasSize(6)))
                .andExpect(jsonPath("$.staffingTrend[0].total").value(0))
                .andExpect(jsonPath("$.staffingTrend[5].total").value(2))
                .andExpect(jsonPath("$.staffingTrend[5].billable").value(1))
                .andExpect(jsonPath("$.staffingTrend[5].month").isNotEmpty())
                // 1 associate fully billable of 3 total -> 33% FTE-weighted utilization
                .andExpect(jsonPath("$.utilizationPercent").value(33))
                // Anita is fresh on the bench (created today)
                .andExpect(jsonPath("$.benchAging.days0to30").value(1))
                .andExpect(jsonPath("$.benchAging.days31to60").value(0))
                .andExpect(jsonPath("$.benchAging.days60plus").value(0))
                .andExpect(jsonPath("$.benchAssociates", hasSize(1)))
                .andExpect(jsonPath("$.benchAssociates[0].name").value("Anita Rao"))
                .andExpect(jsonPath("$.benchAssociates[0].benchDays").value(0))
                // Rahul's allocation ends in 10 days -> roll-off radar
                .andExpect(jsonPath("$.upcomingRolloffs", hasSize(1)))
                .andExpect(jsonPath("$.upcomingRolloffs[0].associateName").value("Rahul Verma"))
                .andExpect(jsonPath("$.upcomingRolloffs[0].projectName").value("Storefront Revamp"))
                .andExpect(jsonPath("$.upcomingRolloffs[0].daysLeft").value(10))
                .andExpect(jsonPath("$.openPositions").value(0));
    }

    @Test
    void summary_withNoData_returnsZeros() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAssociates").value(0))
                .andExpect(jsonPath("$.benchCount").value(0))
                .andExpect(jsonPath("$.clientHeadcounts", hasSize(0)));
    }
}
