package com.softility.omivertex.api;

import com.softility.omivertex.domain.WorkMode;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StaffingApiTest extends ApiTestBase {

    @Test
    void staffing_buildsClientProjectAssociateTree_withBillableSplit() throws Exception {
        var acme = client("Acme Corp");
        var p1 = project("ACM-1", "Data Platform", acme);
        var dev = associate("Asha Iyer", "asha@softility.com", WorkMode.OFFSHORE);
        var qa = associate("Vikram Das", "vikram@softility.com", WorkMode.OFFSHORE);
        allocation(dev, p1, true);   // billable
        allocation(qa, p1, false);   // non-billable, same project

        mockMvc.perform(get("/api/v1/staffing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].clientName").value("Acme Corp"))
                .andExpect(jsonPath("$[0].billable").value(1))
                .andExpect(jsonPath("$[0].nonBillable").value(1))
                .andExpect(jsonPath("$[0].projects", hasSize(1)))
                .andExpect(jsonPath("$[0].projects[0].projectName").value("Data Platform"))
                .andExpect(jsonPath("$[0].projects[0].projectCode").value("ACM-1"))
                .andExpect(jsonPath("$[0].projects[0].billable").value(1))
                .andExpect(jsonPath("$[0].projects[0].nonBillable").value(1))
                .andExpect(jsonPath("$[0].projects[0].associates", hasSize(2)))
                // associates sorted by name: Asha before Vikram
                .andExpect(jsonPath("$[0].projects[0].associates[0].name").value("Asha Iyer"))
                .andExpect(jsonPath("$[0].projects[0].associates[0].billable").value(true))
                .andExpect(jsonPath("$[0].projects[0].associates[1].name").value("Vikram Das"))
                .andExpect(jsonPath("$[0].projects[0].associates[1].billable").value(false));
    }

    @Test
    void staffing_countsAssociateOncePerClient_billableWins() throws Exception {
        // Asha is billable on one Acme project and non-billable on another:
        // client level counts her ONCE, as billable.
        var acme = client("Acme Corp");
        var p1 = project("ACM-1", "Data Platform", acme);
        var p2 = project("ACM-2", "Support Desk", acme);
        var dev = associate("Asha Iyer", "asha@softility.com", WorkMode.OFFSHORE);
        var a1 = allocation(dev, p1, true);
        a1.setAllocationPercent(50);
        allocationRepository.save(a1);
        var a2 = allocation(dev, p2, false);
        a2.setAllocationPercent(50);
        allocationRepository.save(a2);

        mockMvc.perform(get("/api/v1/staffing"))
                .andExpect(jsonPath("$[0].billable").value(1))
                .andExpect(jsonPath("$[0].nonBillable").value(0))
                .andExpect(jsonPath("$[0].projects", hasSize(2)));
    }

    @Test
    void staffing_excludesEndedAllocations() throws Exception {
        var acme = client("Acme Corp");
        var p1 = project("ACM-1", "Data Platform", acme);
        var dev = associate("Asha Iyer", "asha@softility.com", WorkMode.OFFSHORE);
        var ended = allocation(dev, p1, true);
        ended.setEndDate(LocalDate.now().minusDays(10));
        allocationRepository.save(ended);

        mockMvc.perform(get("/api/v1/staffing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithMockUser(username = "viewer", roles = "VIEWER")
    void staffing_isReadableByViewer() throws Exception {
        mockMvc.perform(get("/api/v1/staffing")).andExpect(status().isOk());
    }

    @Test
    void staffing_associateRow_carriesAllocationIdEndDateAndActive() throws Exception {
        var acme = client("Acme Corp");
        var p1 = project("ACM-1", "Data Platform", acme);
        var dev = associate("Asha Iyer", "asha@softility.com", WorkMode.OFFSHORE);
        var alloc = allocation(dev, p1, true);
        alloc.setEndDate(LocalDate.now().plusDays(30));
        allocationRepository.save(alloc);

        mockMvc.perform(get("/api/v1/staffing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].projects[0].associates[0].allocationId").value(alloc.getId()))
                .andExpect(jsonPath("$[0].projects[0].associates[0].endDate")
                        .value(LocalDate.now().plusDays(30).toString()))
                .andExpect(jsonPath("$[0].projects[0].associates[0].active").value(true));
    }

    @Test
    void staffing_includeEnded_showsEndedRowsMarkedInactive_butCountsStayCurrent() throws Exception {
        var acme = client("Acme Corp");
        var p1 = project("ACM-1", "Data Platform", acme);
        var current = associate("Asha Iyer", "asha@softility.com", WorkMode.OFFSHORE);
        var former = associate("Vikram Das", "vikram@softility.com", WorkMode.OFFSHORE);
        allocation(current, p1, true); // current, billable
        var ended = allocation(former, p1, true);
        ended.setEndDate(LocalDate.now().minusDays(10));
        allocationRepository.save(ended);

        // Default: ended row excluded entirely.
        mockMvc.perform(get("/api/v1/staffing"))
                .andExpect(jsonPath("$[0].projects[0].associates", hasSize(1)))
                .andExpect(jsonPath("$[0].projects[0].associates[0].name").value("Asha Iyer"));

        // includeEnded=true: ended row appears, marked inactive, and does NOT move counts.
        mockMvc.perform(get("/api/v1/staffing").param("includeEnded", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].billable").value(1))
                .andExpect(jsonPath("$[0].nonBillable").value(0))
                .andExpect(jsonPath("$[0].projects[0].billable").value(1))
                .andExpect(jsonPath("$[0].projects[0].nonBillable").value(0))
                .andExpect(jsonPath("$[0].projects[0].associates", hasSize(2)))
                // sorted by name: Asha (active) then Vikram (ended)
                .andExpect(jsonPath("$[0].projects[0].associates[0].active").value(true))
                .andExpect(jsonPath("$[0].projects[0].associates[1].name").value("Vikram Das"))
                .andExpect(jsonPath("$[0].projects[0].associates[1].active").value(false));
    }
}
