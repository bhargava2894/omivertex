package com.softility.omivertex.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthApiTest {

    @Autowired
    private MockMvc mockMvc;

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
                                {"name":"Viewer Client"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_canWrite() throws Exception {
        MockHttpSession session = login("admin", "Admin@123");
        mockMvc.perform(post("/api/v1/clients").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Admin Client %s"}""".formatted(System.nanoTime())))
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
}
