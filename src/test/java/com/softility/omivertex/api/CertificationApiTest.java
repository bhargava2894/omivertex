package com.softility.omivertex.api;

import com.softility.omivertex.domain.WorkMode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CertificationApiTest extends ApiTestBase {

    @Test
    void addCertification_thenListForAssociate() throws Exception {
        var dev = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);

        mockMvc.perform(post("/api/v1/associates/" + dev.getId() + "/certifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"AWS Certified Cloud Practitioner","authority":"Amazon Web Services",
                                 "credentialId":"AWS-CCP-12345","issuedDate":"2025-01-15","expiryDate":"2028-01-15"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("AWS Certified Cloud Practitioner"))
                .andExpect(jsonPath("$.associateName").value("Priya Sharma"));

        mockMvc.perform(get("/api/v1/associates/" + dev.getId() + "/certifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].credentialId").value("AWS-CCP-12345"));
    }

    @Test
    void addCertification_blankName_400_unknownAssociate_404() throws Exception {
        var dev = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        mockMvc.perform(post("/api/v1/associates/" + dev.getId() + "/certifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":""}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
        mockMvc.perform(post("/api/v1/associates/9999/certifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"CKA"}"""))
                .andExpect(status().isNotFound());
    }

    @Test
    void orgWideList_searchesAndSortsByExpiry() throws Exception {
        var priya = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        var rahul = associate("Rahul Verma", "rahul@softility.com", WorkMode.ONSHORE);
        cert(priya, "AWS Certified Cloud Practitioner", "Amazon Web Services", LocalDate.now().plusYears(2));
        cert(rahul, "Terraform Associate", "HashiCorp", LocalDate.now().plusMonths(6));

        mockMvc.perform(get("/api/v1/certifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Terraform Associate")); // soonest expiry first

        mockMvc.perform(get("/api/v1/certifications").param("q", "aws"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].associateName").value("Priya Sharma"));

        mockMvc.perform(get("/api/v1/certifications").param("q", "rahul"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Terraform Associate"));
    }

    @Test
    void deleteCertification_removesIt() throws Exception {
        var dev = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        var saved = cert(dev, "CKA", "CNCF", LocalDate.now().plusYears(1));

        mockMvc.perform(delete("/api/v1/certifications/" + saved.getId()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/certifications"))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    private com.softility.omivertex.domain.Certification cert(
            com.softility.omivertex.domain.Associate associate, String name, String authority, LocalDate expiry) {
        var c = new com.softility.omivertex.domain.Certification();
        c.setAssociate(associate);
        c.setName(name);
        c.setAuthority(authority);
        c.setIssuedDate(LocalDate.now().minusYears(1));
        c.setExpiryDate(expiry);
        return certificationRepository.save(c);
    }
}
