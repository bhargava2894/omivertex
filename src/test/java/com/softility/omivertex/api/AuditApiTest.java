package com.softility.omivertex.api;

import com.softility.omivertex.domain.WorkMode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuditApiTest extends ApiTestBase {

    @Test
    void createClient_recordsAuditEntry() throws Exception {
        mockMvc.perform(post("/api/v1/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Acme Corp","clientId":"CLI-001"}"""))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/admin/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("CREATED"))
                .andExpect(jsonPath("$[0].entityType").value("Client"))
                .andExpect(jsonPath("$[0].username").value("admin"))
                .andExpect(jsonPath("$[0].summary", containsString("Acme Corp")))
                .andExpect(jsonPath("$[0].timestamp").isNotEmpty());
    }

    @Test
    void updateAndDelete_recordAuditEntries() throws Exception {
        var saved = client("Acme Corp");
        mockMvc.perform(put("/api/v1/clients/" + saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Acme Corporation","clientId":"CLI-001"}"""))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/clients/" + saved.getId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/admin/audit").param("entityType", "Client"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("DELETED"))
                .andExpect(jsonPath("$[1].action").value("UPDATED"));
    }

    @Test
    void allocationLifecycle_recordsNamedSummaries() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var dev = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);

        mockMvc.perform(post("/api/v1/allocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"associateId":%d,"projectId":%d,"billable":true,"startDate":"2026-07-01"}"""
                                .formatted(dev.getId(), proj.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/admin/audit").param("entityType", "Allocation"))
                .andExpect(jsonPath("$[0].action").value("CREATED"))
                .andExpect(jsonPath("$[0].summary", containsString("Priya Sharma")))
                .andExpect(jsonPath("$[0].summary", containsString("Storefront Revamp")));
    }

    @Test
    void import_recordsSingleAuditEntry() throws Exception {
        String csv = """
                ASSOCIATE NAME,COMPANY,LOCATION,CUSTOMER,BILLABLE,Project
                Madhu Chittepu,Softility,OFFSHORE,COX,B,BIGDATA DEVOPS
                """;
        var file = new org.springframework.mock.web.MockMultipartFile("file", "roster.csv", "text/csv", csv.getBytes());
        mockMvc.perform(multipart("/api/v1/data/import").file(file))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/audit").param("entityType", "Import"))
                .andExpect(jsonPath("$[0].action").value("IMPORTED"))
                .andExpect(jsonPath("$[0].summary", containsString("1")));
    }

    @Test
    @WithMockUser(username = "viewer", roles = "VIEWER")
    void audit_asViewer_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit"))
                .andExpect(status().isForbidden());
    }
}
