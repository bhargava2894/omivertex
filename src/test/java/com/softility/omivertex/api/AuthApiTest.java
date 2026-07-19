package com.softility.omivertex.api;

import com.softility.omivertex.domain.AccessStatus;
import com.softility.omivertex.domain.AppUser;
import com.softility.omivertex.domain.Role;
import com.softility.omivertex.repository.AppUserRepository;
import com.softility.omivertex.service.GoogleTokenVerifier;
import com.softility.omivertex.service.GoogleTokenVerifier.GoogleIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository appUserRepository;

    @MockBean
    private GoogleTokenVerifier googleTokenVerifier;

    @BeforeEach
    void clean() {
        appUserRepository.deleteAll();
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}""".formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    void login_asAdmin_returnsAdminRole() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"Admin@123"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.displayName").value("Super Admin"));
    }

    @Test
    void login_withWrongPassword_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"nope"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void me_withoutSession_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_afterLogin_returnsCurrentUser() throws Exception {
        MockHttpSession session = login("viewer", "Viewer@123");
        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("viewer"))
                .andExpect(jsonPath("$.role").value("VIEWER"));
    }

    @Test
    void apiWithoutLogin_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/associates"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void viewer_canRead_butCannotWrite() throws Exception {
        MockHttpSession session = login("viewer", "Viewer@123");
        mockMvc.perform(get("/api/v1/clients").session(session))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/clients").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Viewer Client","clientId":"CLI-VIEWER"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_canWrite() throws Exception {
        MockHttpSession session = login("admin", "Admin@123");
        mockMvc.perform(post("/api/v1/clients").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Admin Client %s","clientId":"CLI-ADMIN-%s"}""".formatted(System.nanoTime(), System.nanoTime())))
                .andExpect(status().isCreated());
    }

    @Test
    void logout_endsSession() throws Exception {
        MockHttpSession session = login("admin", "Admin@123");
        mockMvc.perform(post("/api/v1/auth/logout").session(session))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    private void googlePost(String idToken, org.springframework.test.web.servlet.ResultMatcher... matchers) throws Exception {
        var actions = mockMvc.perform(post("/api/v1/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"idToken":"%s"}""".formatted(idToken)));
        for (var m : matchers) {
            actions.andExpect(m);
        }
    }

    @Test
    void google_unverifiableToken_returns401_andCreatesNoUser() throws Exception {
        // verifier is an unstubbed mock -> returns Optional.empty() (fails closed)
        googlePost("forged-or-unconfigured", status().isUnauthorized());
        org.junit.jupiter.api.Assertions.assertEquals(0, appUserRepository.count());
    }

    @Test
    void google_verifiedNewCompanyUser_createsPendingAndIsRefused() throws Exception {
        when(googleTokenVerifier.verify("tok-new"))
                .thenReturn(Optional.of(new GoogleIdentity("newhire@softility.com", "New Hire")));

        googlePost("tok-new", status().isUnauthorized(),
                jsonPath("$.message").value(org.hamcrest.Matchers.containsString("pending")));

        AppUser created = appUserRepository.findByEmailIgnoreCase("newhire@softility.com").orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(AccessStatus.PENDING, created.getStatus());
    }

    @Test
    void google_verifiedApprovedUser_returnsOkWithSession() throws Exception {
        AppUser approved = new AppUser();
        approved.setEmail("lead@softility.com");
        approved.setName("Team Lead");
        approved.setRole(Role.ADMIN);
        approved.setStatus(AccessStatus.APPROVED);
        appUserRepository.save(approved);

        when(googleTokenVerifier.verify("tok-approved"))
                .thenReturn(Optional.of(new GoogleIdentity("lead@softility.com", "Team Lead")));

        googlePost("tok-approved", status().isOk(),
                jsonPath("$.username").value("lead@softility.com"),
                jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void google_verifiedNonCompanyEmail_returns400() throws Exception {
        when(googleTokenVerifier.verify("tok-outsider"))
                .thenReturn(Optional.of(new GoogleIdentity("someone@gmail.com", "Outsider")));

        googlePost("tok-outsider", status().isBadRequest());
    }
}
