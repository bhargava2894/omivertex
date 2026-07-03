package com.softility.omivertex.api;

import com.softility.omivertex.domain.WorkMode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AssociateApiTest extends ApiTestBase {

    @Test
    void createAssociate_returnsCreatedAssociate() throws Exception {
        mockMvc.perform(post("/api/v1/associates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Priya Sharma","email":"priya@softility.com","company":"Softility",
                                 "location":"Hyderabad","workMode":"OFFSHORE","designation":"Senior Consultant"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Priya Sharma"))
                .andExpect(jsonPath("$.workMode").value("OFFSHORE"))
                .andExpect(jsonPath("$.billable").value(false))
                .andExpect(jsonPath("$.currentProject").isEmpty());
    }

    @Test
    void createAssociate_invalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/associates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Priya Sharma","email":"not-an-email","company":"Softility","workMode":"OFFSHORE"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    void createAssociate_duplicateEmail_returns409() throws Exception {
        associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        mockMvc.perform(post("/api/v1/associates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Other","email":"PRIYA@softility.com","company":"Softility","workMode":"ONSHORE"}"""))
                .andExpect(status().isConflict());
    }

    @Test
    void listAssociates_includesDerivedAllocationFields() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var allocated = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        associate("Rahul Verma", "rahul@softility.com", WorkMode.ONSHORE);
        allocation(allocated, proj, true);

        mockMvc.perform(get("/api/v1/associates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.name=='Priya Sharma')].billable").value(true))
                .andExpect(jsonPath("$[?(@.name=='Priya Sharma')].currentProject").value("Storefront Revamp"))
                .andExpect(jsonPath("$[?(@.name=='Priya Sharma')].currentClient").value("Acme Corp"))
                .andExpect(jsonPath("$[?(@.name=='Rahul Verma')].billable").value(false));
    }

    @Test
    void listAssociates_benchFilter_returnsOnlyUnallocated() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var allocated = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        associate("Rahul Verma", "rahul@softility.com", WorkMode.ONSHORE);
        allocation(allocated, proj, true);

        mockMvc.perform(get("/api/v1/associates").param("bench", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Rahul Verma"));
    }

    @Test
    void associateResponses_carryBenchDays() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var allocated = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        allocation(allocated, proj, true);
        var rolledOff = associate("Rahul Verma", "rahul@softility.com", WorkMode.ONSHORE);
        var ended = allocation(rolledOff, proj, true);
        ended.setEndDate(java.time.LocalDate.now().minusDays(12));
        allocationRepository.save(ended);
        var fresh = associate("Anita Rao", "anita@softility.com", WorkMode.ONSHORE);

        mockMvc.perform(get("/api/v1/associates/" + allocated.getId()))
                .andExpect(jsonPath("$.benchDays").isEmpty());
        mockMvc.perform(get("/api/v1/associates/" + rolledOff.getId()))
                .andExpect(jsonPath("$.benchDays").value(12));
        mockMvc.perform(get("/api/v1/associates/" + fresh.getId()))
                .andExpect(jsonPath("$.benchDays").value(0));
    }

    @Test
    void getAssociate_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/associates/9999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateAssociate_updatesFields() throws Exception {
        var saved = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        mockMvc.perform(put("/api/v1/associates/" + saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Priya Sharma","email":"priya@softility.com","company":"Softility",
                                 "location":"Dallas","workMode":"ONSHORE","designation":"Lead Consultant"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workMode").value("ONSHORE"))
                .andExpect(jsonPath("$.location").value("Dallas"));
    }

    @Test
    void deleteAssociate_removesAssociate() throws Exception {
        var saved = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        mockMvc.perform(delete("/api/v1/associates/" + saved.getId()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteAssociate_withAllocations_returns409() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var saved = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        allocation(saved, proj, true);
        mockMvc.perform(delete("/api/v1/associates/" + saved.getId()))
                .andExpect(status().isConflict());
    }
}
