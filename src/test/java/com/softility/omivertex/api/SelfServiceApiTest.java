package com.softility.omivertex.api;

import com.softility.omivertex.domain.AccessStatus;
import com.softility.omivertex.domain.AppUser;
import com.softility.omivertex.domain.Associate;
import com.softility.omivertex.domain.Role;
import com.softility.omivertex.domain.WorkMode;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SelfServiceApiTest extends ApiTestBase {

    protected Associate linkedAssociate() {
        var dev = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        var user = new AppUser();
        user.setEmail("priya@softility.com");
        user.setName("Priya Sharma");
        user.setRole(Role.ASSOCIATE);
        user.setStatus(AccessStatus.APPROVED);
        user.setAssociateId(dev.getId());
        appUserRepository.save(user);
        return dev;
    }

    @Test
    @WithMockUser(username = "priya@softility.com", roles = "ASSOCIATE")
    void associate_seesOwnProfile() throws Exception {
        linkedAssociate();
        mockMvc.perform(get("/api/v1/me/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Priya Sharma"));
    }

    @Test
    @WithMockUser(username = "priya@softility.com", roles = "ASSOCIATE")
    void associate_cannotBrowseRoster() throws Exception {
        linkedAssociate();
        mockMvc.perform(get("/api/v1/associates")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/dashboard/summary")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "priya@softility.com", roles = "ASSOCIATE")
    void associate_submitsSkillChange_pendingAndDuplicateBlocked() throws Exception {
        linkedAssociate();
        var java = skill("Backend", "Java");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/me/profile-changes/skills")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"skills":[{"skillId":%d,"proficiency":"ADVANCE","primary":true}]}"""
                                .formatted(java.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("SKILLS"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.proposedSkills[0].skillName").value("Java"));

        // live profile untouched until approval
        mockMvc.perform(get("/api/v1/me/profile"))
                .andExpect(jsonPath("$.skillGroups", org.hamcrest.Matchers.hasSize(0)));

        // second pending SKILLS request -> 409
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/me/profile-changes/skills")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"skills":[{"skillId":%d,"proficiency":"NOVICE","primary":false}]}"""
                                .formatted(java.getId())))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "priya@softility.com", roles = "ASSOCIATE")
    void associate_submitsResumeChange_andListsOwnRequests() throws Exception {
        linkedAssociate();
        var file = new org.springframework.mock.web.MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "PDFDATA".getBytes());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/v1/me/profile-changes/resume").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("RESUME"))
                .andExpect(jsonPath("$.resumeFilename").value("resume.pdf"));

        mockMvc.perform(get("/api/v1/me/profile-changes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }
}
