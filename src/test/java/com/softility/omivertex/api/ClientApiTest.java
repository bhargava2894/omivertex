package com.softility.omivertex.api;

import com.softility.omivertex.domain.Client;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ClientApiTest extends ApiTestBase {

    @Test
    void createClient_returnsCreatedClient() throws Exception {
        mockMvc.perform(post("/api/v1/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Acme Corp","clientId":"CLI-001","industry":"Retail","location":"Chicago"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Acme Corp"))
                .andExpect(jsonPath("$.industry").value("Retail"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void createClient_withBlankName_returns400WithFieldError() throws Exception {
        mockMvc.perform(post("/api/v1/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","clientId":"CLI-001","industry":"Retail"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void createClient_withBlankClientId_returns400WithFieldError() throws Exception {
        mockMvc.perform(post("/api/v1/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Acme Corp","clientId":"","industry":"Retail"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.clientId").exists());
    }

    @Test
    void createClient_withDuplicateName_returns409() throws Exception {
        client("Acme Corp");
        mockMvc.perform(post("/api/v1/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"acme corp","clientId":"CLI-002"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void createClient_withClientId_savesClientId() throws Exception {
        mockMvc.perform(post("/api/v1/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Acme Corp","clientId":"CLI-123","industry":"Retail","location":"Chicago"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientId").value("CLI-123"));
    }

    @Test
    void createClient_duplicateClientId_returns409() throws Exception {
        Client existing = client("Acme Corp");
        existing.setClientId("CLI-123");
        clientRepository.save(existing);

        mockMvc.perform(post("/api/v1/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Other Corp","clientId":"CLI-123","industry":"Healthcare"}"""))
                .andExpect(status().isConflict());
    }

    @Test
    void listClients_returnsAllClients() throws Exception {
        client("Acme Corp");
        client("Globex");
        mockMvc.perform(get("/api/v1/clients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void getClient_returnsClient() throws Exception {
        var saved = client("Acme Corp");
        mockMvc.perform(get("/api/v1/clients/" + saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme Corp"));
    }

    @Test
    void getClient_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/clients/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void updateClient_updatesFields() throws Exception {
        var saved = client("Acme Corp");
        mockMvc.perform(put("/api/v1/clients/" + saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Acme Corporation","clientId":"CLI-001","industry":"Retail","location":"Chicago","status":"INACTIVE"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme Corporation"))
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void deleteClient_removesClient() throws Exception {
        var saved = client("Acme Corp");
        mockMvc.perform(delete("/api/v1/clients/" + saved.getId()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/clients"))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void deleteClient_withProjects_returns409() throws Exception {
        var saved = client("Acme Corp");
        project("ACM-1", "Storefront Revamp", saved);
        mockMvc.perform(delete("/api/v1/clients/" + saved.getId()))
                .andExpect(status().isConflict());
    }
}
