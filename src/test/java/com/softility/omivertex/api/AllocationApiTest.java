package com.softility.omivertex.api;

import com.softility.omivertex.domain.WorkMode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AllocationApiTest extends ApiTestBase {

    @Test
    void createAllocation_assignsAssociateToProject() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var dev = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);

        mockMvc.perform(post("/api/v1/allocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"associateId":%d,"projectId":%d,"billable":true,"allocationPercent":100,"startDate":"2026-06-01"}"""
                                .formatted(dev.getId(), proj.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.associateName").value("Priya Sharma"))
                .andExpect(jsonPath("$.projectName").value("Storefront Revamp"))
                .andExpect(jsonPath("$.clientName").value("Acme Corp"))
                .andExpect(jsonPath("$.billable").value(true));
    }

    @Test
    void createAllocation_unknownAssociate_returns404() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        mockMvc.perform(post("/api/v1/allocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"associateId":9999,"projectId":%d,"billable":true,"startDate":"2026-06-01"}"""
                                .formatted(proj.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void createAllocation_duplicateOpenAllocation_returns409() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var dev = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        allocation(dev, proj, true);

        mockMvc.perform(post("/api/v1/allocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"associateId":%d,"projectId":%d,"billable":false,"startDate":"2026-06-01"}"""
                                .formatted(dev.getId(), proj.getId())))
                .andExpect(status().isConflict());
    }

    @Test
    void createAllocation_invalidPercent_returns400() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var dev = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        mockMvc.perform(post("/api/v1/allocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"associateId":%d,"projectId":%d,"billable":true,"allocationPercent":150,"startDate":"2026-06-01"}"""
                                .formatted(dev.getId(), proj.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.allocationPercent").exists());
    }

    @Test
    void listAllocations_filtersByProjectAndActive() throws Exception {
        var acme = client("Acme Corp");
        var proj1 = project("ACM-100", "Storefront Revamp", acme);
        var proj2 = project("ACM-200", "Mobile App", acme);
        var dev1 = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        var dev2 = associate("Rahul Verma", "rahul@softility.com", WorkMode.ONSHORE);
        allocation(dev1, proj1, true);
        var ended = allocation(dev2, proj2, true);
        ended.setEndDate(LocalDate.now().minusDays(10));
        allocationRepository.save(ended);

        mockMvc.perform(get("/api/v1/allocations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(get("/api/v1/allocations").param("projectId", proj1.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].associateName").value("Priya Sharma"));

        mockMvc.perform(get("/api/v1/allocations").param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].projectName").value("Storefront Revamp"));
    }

    @Test
    void updateAllocation_rollOff_movesAssociateToBench() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var dev = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        var saved = allocation(dev, proj, true);

        mockMvc.perform(put("/api/v1/allocations/" + saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"billable":true,"allocationPercent":100,"startDate":"%s","endDate":"%s"}"""
                                .formatted(saved.getStartDate(), LocalDate.now().minusDays(1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endDate").value(LocalDate.now().minusDays(1).toString()));

        mockMvc.perform(get("/api/v1/associates/" + dev.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billable").value(false))
                .andExpect(jsonPath("$.currentProject").isEmpty());
    }

    @Test
    void deleteAllocation_removesAllocation() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var dev = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        var saved = allocation(dev, proj, true);
        mockMvc.perform(delete("/api/v1/allocations/" + saved.getId()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/allocations"))
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
