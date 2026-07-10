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
}
