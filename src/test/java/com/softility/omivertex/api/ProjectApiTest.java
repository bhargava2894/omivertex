package com.softility.omivertex.api;

import com.softility.omivertex.domain.WorkMode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProjectApiTest extends ApiTestBase {

    @Test
    void createProject_returnsCreatedProjectWithClientName() throws Exception {
        var acme = client("Acme Corp");
        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"ACM-100","name":"Storefront Revamp","clientId":%d,"startDate":"2026-01-01"}"""
                                .formatted(acme.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.code").value("ACM-100"))
                .andExpect(jsonPath("$.clientName").value("Acme Corp"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void createProject_unknownClient_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"ACM-100","name":"Storefront Revamp","clientId":9999}"""))
                .andExpect(status().isNotFound());
    }

    @Test
    void createProject_duplicateCode_returns409() throws Exception {
        var acme = client("Acme Corp");
        project("ACM-100", "Storefront Revamp", acme);
        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"acm-100","name":"Another","clientId":%d}""".formatted(acme.getId())))
                .andExpect(status().isConflict());
    }

    @Test
    void createProject_blankName_returns400() throws Exception {
        var acme = client("Acme Corp");
        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"ACM-100","name":"","clientId":%d}""".formatted(acme.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void listProjects_filtersByClientId() throws Exception {
        var acme = client("Acme Corp");
        var globex = client("Globex");
        project("ACM-100", "Storefront Revamp", acme);
        project("GLX-200", "Data Platform", globex);

        mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(get("/api/v1/projects").param("clientId", acme.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].code").value("ACM-100"));
    }

    @Test
    void updateProject_updatesFields() throws Exception {
        var acme = client("Acme Corp");
        var saved = project("ACM-100", "Storefront Revamp", acme);
        mockMvc.perform(put("/api/v1/projects/" + saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"ACM-100","name":"Storefront Revamp 2.0","clientId":%d,"status":"ON_HOLD"}"""
                                .formatted(acme.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Storefront Revamp 2.0"))
                .andExpect(jsonPath("$.status").value("ON_HOLD"));
    }

    @Test
    void deleteProject_removesProject() throws Exception {
        var acme = client("Acme Corp");
        var saved = project("ACM-100", "Storefront Revamp", acme);
        mockMvc.perform(delete("/api/v1/projects/" + saved.getId()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteProject_withAllocations_returns409() throws Exception {
        var acme = client("Acme Corp");
        var saved = project("ACM-100", "Storefront Revamp", acme);
        var dev = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        allocation(dev, saved, true);
        mockMvc.perform(delete("/api/v1/projects/" + saved.getId()))
                .andExpect(status().isConflict());
    }
}
