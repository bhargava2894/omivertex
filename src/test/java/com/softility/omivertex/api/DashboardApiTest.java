package com.softility.omivertex.api;

import com.softility.omivertex.domain.WorkMode;
import org.junit.jupiter.api.Test;

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
        allocation(shadowDev, proj, false);

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
                .andExpect(jsonPath("$.clientHeadcounts[0].headcount").value(2));
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
