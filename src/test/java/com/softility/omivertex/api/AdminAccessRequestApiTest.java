package com.softility.omivertex.api;

import com.softility.omivertex.domain.AccessStatus;
import com.softility.omivertex.domain.AppUser;
import com.softility.omivertex.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminAccessRequestApiTest extends ApiTestBase {

    private AppUser appUser(String email, AccessStatus status) {
        AppUser u = new AppUser();
        u.setEmail(email);
        u.setName(email.split("@")[0]);
        u.setRole(Role.VIEWER);
        u.setStatus(status);
        return appUserRepository.save(u);
    }

    @Test
    void listRequests_returnsAllAsDtos() throws Exception {
        appUser("pending@softility.com", AccessStatus.PENDING);
        appUser("approved@softility.com", AccessStatus.APPROVED);

        mockMvc.perform(get("/api/v1/admin/access-requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].email").exists())
                .andExpect(jsonPath("$[0].status").exists());
    }

    @Test
    void approve_setsApproved() throws Exception {
        var u = appUser("newbie@softility.com", AccessStatus.PENDING);

        mockMvc.perform(post("/api/v1/admin/access-requests/" + u.getId() + "/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        org.junit.jupiter.api.Assertions.assertEquals(
                AccessStatus.APPROVED, appUserRepository.findById(u.getId()).orElseThrow().getStatus());
    }

    @Test
    void approve_withAdminRole_promotesUser() throws Exception {
        var u = appUser("lead@softility.com", AccessStatus.PENDING);

        mockMvc.perform(post("/api/v1/admin/access-requests/" + u.getId() + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"ADMIN"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.role").value("ADMIN"));

        var saved = appUserRepository.findById(u.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(Role.ADMIN, saved.getRole());
        org.junit.jupiter.api.Assertions.assertEquals(AccessStatus.APPROVED, saved.getStatus());
    }

    @Test
    void approve_withoutBody_defaultsToViewer() throws Exception {
        var u = appUser("newbie@softility.com", AccessStatus.PENDING);

        mockMvc.perform(post("/api/v1/admin/access-requests/" + u.getId() + "/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.role").value("VIEWER"));
    }

    @Test
    void approveAsAssociate_linksRosterRecord() throws Exception {
        var dev = associate("Priya Sharma", "priya@softility.com", com.softility.omivertex.domain.WorkMode.OFFSHORE);
        var u = appUser("priya@softility.com", AccessStatus.PENDING);

        mockMvc.perform(post("/api/v1/admin/access-requests/" + u.getId() + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"ASSOCIATE"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ASSOCIATE"));

        var saved = appUserRepository.findById(u.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(dev.getId(), saved.getAssociateId());
    }

    @Test
    void approveAsAssociate_withoutRosterMatch_returns400() throws Exception {
        var u = appUser("stranger@softility.com", AccessStatus.PENDING);
        mockMvc.perform(post("/api/v1/admin/access-requests/" + u.getId() + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"ASSOCIATE"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reject_setsRejected() throws Exception {
        var u = appUser("nope@softility.com", AccessStatus.PENDING);

        mockMvc.perform(post("/api/v1/admin/access-requests/" + u.getId() + "/reject"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void approve_unknownId_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/admin/access-requests/9999/approve"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "viewer", roles = "VIEWER")
    void viewer_cannotAccessAdminRequests() throws Exception {
        mockMvc.perform(get("/api/v1/admin/access-requests"))
                .andExpect(status().isForbidden());
    }
}
