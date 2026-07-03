package com.softility.omivertex.api;

import com.softility.omivertex.domain.Proficiency;
import com.softility.omivertex.domain.WorkMode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TaxonomyApiTest extends ApiTestBase {

    @Test
    void taxonomy_listsCategoriesWithNestedSkills() throws Exception {
        skill("CI/CD", "Jenkins");
        skill("CI/CD", "GitHub");
        skill("Cloud Platforms", "AWS");

        mockMvc.perform(get("/api/v1/taxonomy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("CI/CD"))
                .andExpect(jsonPath("$[0].skills", hasSize(2)))
                .andExpect(jsonPath("$[0].skills[0].name").value("GitHub"))
                .andExpect(jsonPath("$[1].name").value("Cloud Platforms"));
    }

    @Test
    void createCategory_worksAndRejectsDuplicatesAndBlank() throws Exception {
        mockMvc.perform(post("/api/v1/taxonomy/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Observability"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Observability"));

        mockMvc.perform(post("/api/v1/taxonomy/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"observability"}"""))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/taxonomy/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":""}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void createSkill_worksAndValidates() throws Exception {
        var existing = skill("DB", "MySQL");
        Long categoryId = existing.getCategory().getId();

        mockMvc.perform(post("/api/v1/taxonomy/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"PostgreSQL","categoryId":%d}""".formatted(categoryId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("PostgreSQL"));

        mockMvc.perform(post("/api/v1/taxonomy/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"mysql","categoryId":%d}""".formatted(categoryId)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/taxonomy/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Redis","categoryId":9999}"""))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteSkill_blockedWhileRated_thenWorks() throws Exception {
        var jenkins = skill("CI/CD", "Jenkins");
        var dev = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        rateSkill(dev, jenkins, Proficiency.INTERMEDIATE);

        mockMvc.perform(delete("/api/v1/taxonomy/skills/" + jenkins.getId()))
                .andExpect(status().isConflict());

        associateSkillRepository.deleteAll();
        mockMvc.perform(delete("/api/v1/taxonomy/skills/" + jenkins.getId()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteCategory_blockedWhileItHasSkills() throws Exception {
        var jenkins = skill("CI/CD", "Jenkins");
        Long categoryId = jenkins.getCategory().getId();

        mockMvc.perform(delete("/api/v1/taxonomy/categories/" + categoryId))
                .andExpect(status().isConflict());

        skillRepository.deleteAll();
        mockMvc.perform(delete("/api/v1/taxonomy/categories/" + categoryId))
                .andExpect(status().isNoContent());
    }
}
