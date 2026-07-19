package com.softility.omivertex.api;

import com.softility.omivertex.domain.Skill;
import com.softility.omivertex.domain.WorkMode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AssociateApiTest extends ApiTestBase {

    @Test
    void createAssociate_returnsCreatedAssociate() throws Exception {
        mockMvc.perform(post("/api/v1/associates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Priya Sharma","email":"priya@softility.com","company":"Softility",
                                 "location":"Hyderabad","workMode":"OFFSHORE","designation":"Senior Consultant"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Priya Sharma"))
                .andExpect(jsonPath("$.workMode").value("OFFSHORE"))
                .andExpect(jsonPath("$.billable").value(false))
                .andExpect(jsonPath("$.currentProject").isEmpty());
    }

    @Test
    void createAssociate_invalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/associates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Priya Sharma","email":"not-an-email","company":"Softility","workMode":"OFFSHORE"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    void createAssociate_duplicateEmail_returns409() throws Exception {
        associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        mockMvc.perform(post("/api/v1/associates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Other","email":"PRIYA@softility.com","company":"Softility","workMode":"ONSHORE"}"""))
                .andExpect(status().isConflict());
    }

    @Test
    void listAssociates_includesDerivedAllocationFields() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var allocated = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        associate("Rahul Verma", "rahul@softility.com", WorkMode.ONSHORE);
        allocation(allocated, proj, true);

        mockMvc.perform(get("/api/v1/associates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.name=='Priya Sharma')].billable").value(true))
                .andExpect(jsonPath("$[?(@.name=='Priya Sharma')].currentProject").value("Storefront Revamp"))
                .andExpect(jsonPath("$[?(@.name=='Priya Sharma')].currentClient").value("Acme Corp"))
                .andExpect(jsonPath("$[?(@.name=='Rahul Verma')].billable").value(false));
    }

    @Test
    void listAssociates_benchFilter_returnsOnlyUnallocated() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var allocated = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        associate("Rahul Verma", "rahul@softility.com", WorkMode.ONSHORE);
        allocation(allocated, proj, true);

        mockMvc.perform(get("/api/v1/associates").param("bench", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Rahul Verma"));
    }

    @Test
    void createAssociate_withSkills_derivesHeadlineFromStar() throws Exception {
        Skill java = skill("Programming & Scripting", "Java");
        Skill aws = skill("Cloud Platforms", "AWS");

        mockMvc.perform(post("/api/v1/associates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Priya Sharma","email":"priya@softility.com","company":"Softility",
                                 "workMode":"OFFSHORE","skills":[
                                   {"skillId":%d,"proficiency":"INTERMEDIATE","primary":true},
                                   {"skillId":%d,"proficiency":"ADVANCE","primary":false}]}"""
                                .formatted(java.getId(), aws.getId())))
                .andExpect(status().isCreated())
                // starred Java wins over AWS despite AWS's higher proficiency
                .andExpect(jsonPath("$.primarySkill").value("Java"))
                .andExpect(jsonPath("$.secondarySkill").value("AWS"))
                // groups are alphabetical by category: Cloud Platforms, then Programming...
                .andExpect(jsonPath("$.skillGroups[0].skills[0].name").value("AWS"))
                .andExpect(jsonPath("$.skillGroups[0].skills[0].primary").value(false))
                .andExpect(jsonPath("$.skillGroups[1].skills[0].name").value("Java"))
                .andExpect(jsonPath("$.skillGroups[1].skills[0].primary").value(true));
    }

    @Test
    void createAssociate_withTwoPrimarySkills_returnsBadRequest() throws Exception {
        Skill java = skill("Programming & Scripting", "Java");
        Skill aws = skill("Cloud Platforms", "AWS");

        mockMvc.perform(post("/api/v1/associates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Priya Sharma","email":"priya@softility.com","company":"Softility",
                                 "workMode":"OFFSHORE","skills":[
                                   {"skillId":%d,"proficiency":"ADVANCE","primary":true},
                                   {"skillId":%d,"proficiency":"ADVANCE","primary":true}]}"""
                                .formatted(java.getId(), aws.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void replaceSkills_upsertsRatedSkillsGroupedByCategory() throws Exception {
        var dev = associate("Priya Sharma", "priya@softility.com", com.softility.omivertex.domain.WorkMode.OFFSHORE);
        var jenkins = skill("CI/CD", "Jenkins");
        var aws = skill("Cloud Platforms", "AWS");

        mockMvc.perform(put("/api/v1/associates/" + dev.getId() + "/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"skills":[{"skillId":%d,"proficiency":"INTERMEDIATE"},{"skillId":%d,"proficiency":"MASTERY"}]}"""
                                .formatted(jenkins.getId(), aws.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skillGroups", hasSize(2)))
                .andExpect(jsonPath("$.skillGroups[?(@.category=='CI/CD')].skills[0].name").value("Jenkins"))
                .andExpect(jsonPath("$.skillGroups[?(@.category=='CI/CD')].skills[0].proficiency").value("INTERMEDIATE"))
                .andExpect(jsonPath("$.skillGroups[?(@.category=='Cloud Platforms')].skills[0].proficiency").value("MASTERY"));

        // replace is total: second PUT with one skill drops the other
        mockMvc.perform(put("/api/v1/associates/" + dev.getId() + "/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"skills":[{"skillId":%d,"proficiency":"ADVANCE"}]}""".formatted(jenkins.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skillGroups", hasSize(1)))
                .andExpect(jsonPath("$.skillGroups[0].skills[0].proficiency").value("ADVANCE"));
    }

    @Test
    void replaceSkills_unknownSkill_returns404() throws Exception {
        var dev = associate("Priya Sharma", "priya@softility.com", com.softility.omivertex.domain.WorkMode.OFFSHORE);
        mockMvc.perform(put("/api/v1/associates/" + dev.getId() + "/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"skills":[{"skillId":9999,"proficiency":"NOVICE"}]}"""))
                .andExpect(status().isNotFound());
    }

    @Test
    void listAssociates_includesSkillGroups() throws Exception {
        var dev = associate("Priya Sharma", "priya@softility.com", com.softility.omivertex.domain.WorkMode.OFFSHORE);
        rateSkill(dev, skill("DB", "MySQL"), com.softility.omivertex.domain.Proficiency.FOUNDATIONAL);

        mockMvc.perform(get("/api/v1/associates"))
                .andExpect(jsonPath("$[0].skillGroups[0].category").value("DB"))
                .andExpect(jsonPath("$[0].skillGroups[0].skills[0].name").value("MySQL"));
    }

    @Test
    void associateResponses_carryBenchDays() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var allocated = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        allocation(allocated, proj, true);
        var rolledOff = associate("Rahul Verma", "rahul@softility.com", WorkMode.ONSHORE);
        var ended = allocation(rolledOff, proj, true);
        ended.setEndDate(java.time.LocalDate.now().minusDays(12));
        allocationRepository.save(ended);
        var fresh = associate("Anita Rao", "anita@softility.com", WorkMode.ONSHORE);

        mockMvc.perform(get("/api/v1/associates/" + allocated.getId()))
                .andExpect(jsonPath("$.benchDays").isEmpty());
        mockMvc.perform(get("/api/v1/associates/" + rolledOff.getId()))
                .andExpect(jsonPath("$.benchDays").value(12));
        mockMvc.perform(get("/api/v1/associates/" + fresh.getId()))
                .andExpect(jsonPath("$.benchDays").value(0));
    }

    @Test
    void updateAssociate_recordsExit() throws Exception {
        var dev = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        mockMvc.perform(put("/api/v1/associates/" + dev.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Priya Sharma","email":"priya@softility.com","company":"Softility",
                                 "workMode":"OFFSHORE","exitReason":"RESIGNED",
                                 "resignationDate":"%s","lastWorkingDay":"%s"}"""
                                .formatted(java.time.LocalDate.now(), java.time.LocalDate.now().plusDays(30))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitReason").value("RESIGNED"))
                .andExpect(jsonPath("$.lastWorkingDay").value(java.time.LocalDate.now().plusDays(30).toString()));
    }

    @Test
    void exitReasonWithoutLastWorkingDay_returns400() throws Exception {
        var dev = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        mockMvc.perform(put("/api/v1/associates/" + dev.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Priya Sharma","email":"priya@softility.com","company":"Softility",
                                 "workMode":"OFFSHORE","exitReason":"RESIGNED"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resignationAfterLastWorkingDay_returns400() throws Exception {
        var dev = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        mockMvc.perform(put("/api/v1/associates/" + dev.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Priya Sharma","email":"priya@softility.com","company":"Softility",
                                 "workMode":"OFFSHORE","exitReason":"RESIGNED",
                                 "resignationDate":"%s","lastWorkingDay":"%s"}"""
                                .formatted(java.time.LocalDate.now().plusDays(10), java.time.LocalDate.now())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void benchDays_fallBackToJoinedDateWhenNeverAllocated() throws Exception {
        // roster record created today for someone who joined 45 days ago: the bench
        // clock must count from their join date, not from when the row was imported
        var veteran = associate("Meena Pillai", "meena@softility.com", WorkMode.ONSHORE);
        veteran.setJoinedDate(java.time.LocalDate.now().minusDays(45));
        associateRepository.save(veteran);

        mockMvc.perform(get("/api/v1/associates/" + veteran.getId()))
                .andExpect(jsonPath("$.joinedDate").value(java.time.LocalDate.now().minusDays(45).toString()))
                .andExpect(jsonPath("$.benchDays").value(45));
    }

    @Test
    void createAssociate_acceptsJoinedDate() throws Exception {
        mockMvc.perform(post("/api/v1/associates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Meena Pillai","email":"meena@softility.com","company":"Softility",
                                 "workMode":"ONSHORE","joinedDate":"2026-01-15"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.joinedDate").value("2026-01-15"));
    }

    @Test
    void listAssociates_paginatesWhenPageSupplied() throws Exception {
        associate("A One", "a1@softility.com", WorkMode.ONSHORE);
        associate("B Two", "b2@softility.com", WorkMode.OFFSHORE);
        associate("C Three", "c3@softility.com", WorkMode.ONSHORE);

        mockMvc.perform(get("/api/v1/associates").param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/api/v1/associates").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));

        mockMvc.perform(get("/api/v1/associates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void getAssociate_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/associates/9999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateAssociate_updatesFields() throws Exception {
        var saved = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        mockMvc.perform(put("/api/v1/associates/" + saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Priya Sharma","email":"priya@softility.com","company":"Softility",
                                 "location":"Dallas","workMode":"ONSHORE","designation":"Lead Consultant"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workMode").value("ONSHORE"))
                .andExpect(jsonPath("$.location").value("Dallas"));
    }

    @Test
    void deleteAssociate_removesAssociate() throws Exception {
        var saved = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        mockMvc.perform(delete("/api/v1/associates/" + saved.getId()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteAssociate_withAllocations_returns409() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var saved = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        allocation(saved, proj, true);
        mockMvc.perform(delete("/api/v1/associates/" + saved.getId()))
                .andExpect(status().isConflict());
    }

    @Test
    void listAssociates_facetedSkillSearch_filtersCorrectly() throws Exception {
        var priya = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        var rahul = associate("Rahul Verma", "rahul@softility.com", WorkMode.ONSHORE);
        var anita = associate("Anita Rao", "anita@softility.com", WorkMode.OFFSHORE);

        var db = skill("Database", "PostgreSQL");
        var cloud = skill("Cloud", "AWS");
        var devops = skill("Cloud", "Docker");

        rateSkill(priya, db, com.softility.omivertex.domain.Proficiency.MASTERY);
        rateSkill(rahul, cloud, com.softility.omivertex.domain.Proficiency.FOUNDATIONAL);
        rateSkill(anita, devops, com.softility.omivertex.domain.Proficiency.INTERMEDIATE);

        // 1. Filter by category
        mockMvc.perform(get("/api/v1/associates").param("categoryId", String.valueOf(cloud.getCategory().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2))) // rahul and anita
                .andExpect(jsonPath("$[?(@.name=='Rahul Verma')]").exists())
                .andExpect(jsonPath("$[?(@.name=='Anita Rao')]").exists());

        // 2. Filter by skill
        mockMvc.perform(get("/api/v1/associates").param("skillId", String.valueOf(cloud.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Rahul Verma"));

        // 3. Filter by skill + min proficiency (should match)
        mockMvc.perform(get("/api/v1/associates")
                        .param("skillId", String.valueOf(cloud.getId()))
                        .param("minProficiency", "FOUNDATIONAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        // 4. Filter by skill + min proficiency (should NOT match)
        mockMvc.perform(get("/api/v1/associates")
                        .param("skillId", String.valueOf(cloud.getId()))
                        .param("minProficiency", "MASTERY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // 5. Filter by category + min proficiency
        mockMvc.perform(get("/api/v1/associates")
                        .param("categoryId", String.valueOf(cloud.getCategory().getId()))
                        .param("minProficiency", "INTERMEDIATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1))) // only Anita matches, Rahul is excluded
                .andExpect(jsonPath("$[0].name").value("Anita Rao"));
    }

    @Test
    void create_withPhoneAndEmploymentHistory_persistsAndEchoes() throws Exception {
        String body = """
                {"name":"Priya Sharma","email":"priya@softility.com","company":"Softility",
                 "workMode":"OFFSHORE","phone":"+91 98765 43210",
                 "employmentHistory":[
                   {"company":"Globex","title":"Senior Engineer","startDate":"2021-03-01","endDate":null},
                   {"company":"Initech","title":"Engineer","startDate":"2018-06-01","endDate":"2021-02-01"}]}""";

        mockMvc.perform(post("/api/v1/associates")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phone").value("+91 98765 43210"))
                .andExpect(jsonPath("$.employmentHistory[0].company").value("Globex"))
                .andExpect(jsonPath("$.employmentHistory[1].company").value("Initech"));

        var saved = associateRepository.findAll().get(0);
        assertThat(employmentHistoryRepository.findByAssociateIdOrderBySortOrderAsc(saved.getId()))
                .extracting(com.softility.omivertex.domain.EmploymentHistory::getCompany)
                .containsExactly("Globex", "Initech"); // résumé order kept
    }

    @Test
    void update_ignoresEmploymentHistory_createOnlyBySpec() throws Exception {
        String createBody = """
                {"name":"Priya Sharma","email":"priya@softility.com","company":"Softility",
                 "workMode":"OFFSHORE",
                 "employmentHistory":[{"company":"Globex","title":"Senior Engineer","startDate":"2021-03-01","endDate":null}]}""";
        mockMvc.perform(post("/api/v1/associates")
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated());
        Long id = associateRepository.findAll().get(0).getId();

        String updateBody = """
                {"name":"Priya Sharma","email":"priya@softility.com","company":"Softility",
                 "workMode":"OFFSHORE",
                 "employmentHistory":[{"company":"Hooli","title":"CTO","startDate":null,"endDate":null}]}""";
        mockMvc.perform(put("/api/v1/associates/" + id)
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody))
                .andExpect(status().isOk())
                // the response reflects PERSISTED history, not the ignored input
                .andExpect(jsonPath("$.employmentHistory[0].company").value("Globex"));

        assertThat(employmentHistoryRepository.findByAssociateIdOrderBySortOrderAsc(id))
                .extracting(com.softility.omivertex.domain.EmploymentHistory::getCompany)
                .containsExactly("Globex"); // unchanged — Hooli was ignored
    }

    @Test
    void create_withoutHistory_unchanged() throws Exception {
        String body = """
                {"name":"Rahul Verma","email":"rahul@softility.com","company":"Softility",
                 "workMode":"ONSHORE"}""";

        mockMvc.perform(post("/api/v1/associates")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        assertThat(employmentHistoryRepository.count()).isZero();
    }

    @Test
    void getAssociate_includesResumeFilename() throws Exception {
        var priya = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);

        // When no resume, should be null
        mockMvc.perform(get("/api/v1/associates/" + priya.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumeFilename").isEmpty());

        // Save a dummy resume directly to repository
        var resume = new com.softility.omivertex.domain.Resume();
        resume.setAssociateId(priya.getId());
        resume.setFilename("priya_resume.pdf");
        resume.setContentType("application/pdf");
        resume.setByteSize(1234);
        resume.setContent(new byte[]{1, 2, 3});
        resume.setUploadedAt(java.time.Instant.now());
        resumeRepository.save(resume);

        // When resume exists, should return filename
        mockMvc.perform(get("/api/v1/associates/" + priya.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumeFilename").value("priya_resume.pdf"));
    }
}
