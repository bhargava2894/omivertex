package com.softility.omivertex.api;

import com.softility.omivertex.domain.WorkMode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PositionApiTest extends ApiTestBase {

    private String positionJson(Long projectId) {
        return """
                {"title":"Senior Java Developer","projectId":%d,"requiredSkill":"Java",
                 "billable":true,"allocationPercent":100,"startDate":"%s"}"""
                .formatted(projectId, LocalDate.now());
    }

    @Test
    void createPosition_returnsCreatedWithProjectAndClient() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);

        mockMvc.perform(post("/api/v1/positions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(positionJson(proj.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Senior Java Developer"))
                .andExpect(jsonPath("$.projectName").value("Storefront Revamp"))
                .andExpect(jsonPath("$.clientName").value("Acme Corp"))
                .andExpect(jsonPath("$.requiredSkill").value("Java"))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void createPosition_blankTitle_returns400() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        mockMvc.perform(post("/api/v1/positions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"","projectId":%d}""".formatted(proj.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists());
    }

    @Test
    void createPosition_unknownProject_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/positions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(positionJson(9999L)))
                .andExpect(status().isNotFound());
    }

    @Test
    void createPosition_withHeadcount_savesAndReturnsIt() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);

        mockMvc.perform(post("/api/v1/positions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Senior Java Developer","projectId":%d,"requiredSkill":"Java",
                                 "billable":true,"allocationPercent":100,"headcount":3,"startDate":"%s"}"""
                                .formatted(proj.getId(), LocalDate.now())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.headcount").value(3));
    }

    @Test
    void createPosition_invalidHeadcount_returns400() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);

        mockMvc.perform(post("/api/v1/positions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Senior Java Developer","projectId":%d,"requiredSkill":"Java",
                                 "billable":true,"allocationPercent":100,"headcount":0,"startDate":"%s"}"""
                                .formatted(proj.getId(), LocalDate.now())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.headcount").exists());
    }

    @Test
    void listPositions_filtersByStatus() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        mockMvc.perform(post("/api/v1/positions").contentType(MediaType.APPLICATION_JSON)
                .content(positionJson(proj.getId()))).andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/positions").param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get("/api/v1/positions").param("status", "FILLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void matches_ranksBenchWithMatchingSkillFirst() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var other = project("ACM-200", "Mobile App", acme);

        // bench + java skill -> best
        var benchJava = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        benchJava.setPrimarySkill("Java");
        associateRepository.save(benchJava);
        // bench, different skill
        var benchOther = associate("Rahul Verma", "rahul@softility.com", WorkMode.ONSHORE);
        benchOther.setPrimarySkill("React");
        associateRepository.save(benchOther);
        // java skill but fully allocated -> excluded (no capacity)
        var busyJava = associate("Anita Rao", "anita@softility.com", WorkMode.ONSHORE);
        busyJava.setPrimarySkill("Java");
        associateRepository.save(busyJava);
        allocation(busyJava, other, true);

        var created = mockMvc.perform(post("/api/v1/positions").contentType(MediaType.APPLICATION_JSON)
                        .content(positionJson(proj.getId())))
                .andExpect(status().isCreated()).andReturn();
        long id = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/v1/positions/" + id + "/matches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Priya Sharma"))
                .andExpect(jsonPath("$[0].fullMatch").value(true))
                .andExpect(jsonPath("$[0].availablePercent").value(100))
                .andExpect(jsonPath("$[1].name").value("Rahul Verma"))
                .andExpect(jsonPath("$[1].fullMatch").value(false));
    }

    @Test
    void fill_allocatesAssociateAndMarksFilled() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var dev = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);

        var created = mockMvc.perform(post("/api/v1/positions").contentType(MediaType.APPLICATION_JSON)
                        .content(positionJson(proj.getId())))
                .andExpect(status().isCreated()).andReturn();
        long id = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/v1/positions/" + id + "/fill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"associateId":%d}""".formatted(dev.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FILLED"));

        mockMvc.perform(get("/api/v1/associates/" + dev.getId()))
                .andExpect(jsonPath("$.currentProject").value("Storefront Revamp"))
                .andExpect(jsonPath("$.billable").value(true));
    }

    @Test
    void fill_futureDatedPosition_allocatesForThePositionsPeriod() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var dev = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        LocalDate seatStart = LocalDate.now().plusDays(30);
        LocalDate seatEnd = LocalDate.now().plusDays(90);

        var created = mockMvc.perform(post("/api/v1/positions").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Senior Java Developer","projectId":%d,"requiredSkill":"Java",
                                 "billable":true,"allocationPercent":100,
                                 "startDate":"%s","endDate":"%s"}"""
                                .formatted(proj.getId(), seatStart, seatEnd)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.endDate").value(seatEnd.toString()))
                .andReturn();
        long id = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/v1/positions/" + id + "/fill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"associateId":%d}""".formatted(dev.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FILLED"));

        // the allocation carries the position's engagement window, so capacity is
        // only consumed for that period and the roll-off radar can see the end
        mockMvc.perform(get("/api/v1/allocations").param("associateId", dev.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].startDate").value(seatStart.toString()))
                .andExpect(jsonPath("$[0].endDate").value(seatEnd.toString()))
                .andExpect(jsonPath("$[0].active").value(false));

        // seat hasn't started -> not staffed today, still free for interim work
        mockMvc.perform(get("/api/v1/associates/" + dev.getId()))
                .andExpect(jsonPath("$.currentProject").isEmpty());
    }

    @Test
    void createPosition_endDateBeforeStartDate_returns400() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        mockMvc.perform(post("/api/v1/positions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Senior Java Developer","projectId":%d,
                                 "startDate":"%s","endDate":"%s"}"""
                                .formatted(proj.getId(), LocalDate.now().plusDays(30), LocalDate.now().plusDays(10))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fill_alreadyFilled_returns409() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var dev = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        var dev2 = associate("Rahul Verma", "rahul@softility.com", WorkMode.ONSHORE);

        var created = mockMvc.perform(post("/api/v1/positions").contentType(MediaType.APPLICATION_JSON)
                        .content(positionJson(proj.getId())))
                .andExpect(status().isCreated()).andReturn();
        long id = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/v1/positions/" + id + "/fill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"associateId":%d}""".formatted(dev.getId())))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/positions/" + id + "/fill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"associateId":%d}""".formatted(dev2.getId())))
                .andExpect(status().isConflict());
    }

    @Test
    void fill_overCapacity_returns409AndKeepsPositionOpen() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var other = project("ACM-200", "Mobile App", acme);
        var dev = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        allocation(dev, other, true); // 100% busy

        var created = mockMvc.perform(post("/api/v1/positions").contentType(MediaType.APPLICATION_JSON)
                        .content(positionJson(proj.getId())))
                .andExpect(status().isCreated()).andReturn();
        long id = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/v1/positions/" + id + "/fill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"associateId":%d}""".formatted(dev.getId())))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/v1/positions/" + id))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void updateAndDeletePosition_work() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var created = mockMvc.perform(post("/api/v1/positions").contentType(MediaType.APPLICATION_JSON)
                        .content(positionJson(proj.getId())))
                .andExpect(status().isCreated()).andReturn();
        long id = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(put("/api/v1/positions/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Lead Java Developer","projectId":%d,"requiredSkill":"Java",
                                 "billable":true,"allocationPercent":50,"status":"CANCELLED"}"""
                                .formatted(proj.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Lead Java Developer"))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(delete("/api/v1/positions/" + id))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/positions/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void createPosition_withStructuredSkillAndMatches() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var javaSkill = skill("Development", "Java");

        var priya = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        var rahul = associate("Rahul Verma", "rahul@softility.com", WorkMode.ONSHORE);

        rateSkill(priya, javaSkill, com.softility.omivertex.domain.Proficiency.MASTERY);
        rateSkill(rahul, javaSkill, com.softility.omivertex.domain.Proficiency.INTERMEDIATE);

        // Create position requiring Java with min proficiency ADVANCE
        var created = mockMvc.perform(post("/api/v1/positions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Lead Java Dev","projectId":%d,
                                 "skills":[{"skillId":%d,"minProficiency":"ADVANCE","required":true}],
                                 "billable":true,"allocationPercent":100}"""
                                .formatted(proj.getId(), javaSkill.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.skills", hasSize(1)))
                .andExpect(jsonPath("$.skills[0].skillId").value(javaSkill.getId()))
                .andExpect(jsonPath("$.skills[0].skillName").value("Java"))
                .andExpect(jsonPath("$.skills[0].minProficiency").value("ADVANCE"))
                .andReturn();

        long id = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(created.getResponse().getContentAsString()).get("id").asLong();

        // Priya is MASTERY (>= ADVANCE), Rahul is INTERMEDIATE (< ADVANCE)
        mockMvc.perform(get("/api/v1/positions/" + id + "/matches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Priya Sharma"))
                .andExpect(jsonPath("$[0].fullMatch").value(true))
                .andExpect(jsonPath("$[0].matchedSkills[0]").value("Java"))
                .andExpect(jsonPath("$[1].name").value("Rahul Verma"))
                .andExpect(jsonPath("$[1].fullMatch").value(false))
                .andExpect(jsonPath("$[1].missingRequirements[0]").value("Java (min ADVANCE)"));
    }

    @Test
    void createPosition_withSkillRequirements() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var java = skill("Backend", "Java");
        var aws = skill("Cloud", "AWS");

        mockMvc.perform(post("/api/v1/positions").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Senior Java Developer","projectId":%d,"workMode":"ONSHORE",
                                 "skills":[{"skillId":%d,"minProficiency":"INTERMEDIATE","required":true},
                                           {"skillId":%d,"required":false}]}"""
                                .formatted(proj.getId(), java.getId(), aws.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.workMode").value("ONSHORE"))
                .andExpect(jsonPath("$.skills", hasSize(2)))
                .andExpect(jsonPath("$.skills[0].skillName").value("Java"))
                .andExpect(jsonPath("$.skills[0].required").value(true))
                .andExpect(jsonPath("$.skills[1].skillName").value("AWS"))
                .andExpect(jsonPath("$.skills[1].required").value(false));
    }

    @Test
    void createPosition_duplicateSkill_returns400() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var java = skill("Backend", "Java");
        mockMvc.perform(post("/api/v1/positions").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Dev","projectId":%d,
                                 "skills":[{"skillId":%d,"required":true},{"skillId":%d,"required":false}]}"""
                                .formatted(proj.getId(), java.getId(), java.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void matches_ranksFullMatchesAbovePartialsWithMissingLabels() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var java = skill("Backend", "Java");
        var aws = skill("Cloud", "AWS");
        var k8s = skill("Cloud", "Kubernetes");

        var full = associate("Priya Sharma", "priya@softility.com", WorkMode.ONSHORE);
        rateSkill(full, java, com.softility.omivertex.domain.Proficiency.ADVANCE);
        rateSkill(full, aws, com.softility.omivertex.domain.Proficiency.INTERMEDIATE);
        rateSkill(full, k8s, com.softility.omivertex.domain.Proficiency.FOUNDATIONAL);

        var partial = associate("Rahul Verma", "rahul@softility.com", WorkMode.ONSHORE);
        rateSkill(partial, java, com.softility.omivertex.domain.Proficiency.ADVANCE); // missing AWS

        var offshore = associate("Anita Rao", "anita@softility.com", WorkMode.OFFSHORE);
        rateSkill(offshore, java, com.softility.omivertex.domain.Proficiency.ADVANCE);
        rateSkill(offshore, aws, com.softility.omivertex.domain.Proficiency.ADVANCE); // skills ok, wrong shore

        var created = mockMvc.perform(post("/api/v1/positions").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Senior Java Developer","projectId":%d,"workMode":"ONSHORE",
                                 "skills":[{"skillId":%d,"minProficiency":"INTERMEDIATE","required":true},
                                           {"skillId":%d,"required":true},
                                           {"skillId":%d,"required":false}]}"""
                                .formatted(proj.getId(), java.getId(), aws.getId(), k8s.getId())))
                .andExpect(status().isCreated()).andReturn();
        long id = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/v1/positions/" + id + "/matches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].name").value("Priya Sharma"))
                .andExpect(jsonPath("$[0].fullMatch").value(true))
                .andExpect(jsonPath("$[0].missingRequirements", hasSize(0)))
                .andExpect(jsonPath("$[1].fullMatch").value(false))
                .andExpect(jsonPath("$[2].fullMatch").value(false));
    }

    @Test
    void createPosition_withJobDescription_savesAndReturnsIt() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);

        mockMvc.perform(post("/api/v1/positions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Senior Java Developer","projectId":%d,"requiredSkill":"Java",
                                 "billable":true,"allocationPercent":100,"startDate":"%s",
                                 "jobDescription":"Need a Java developer with AWS experience"}"""
                                .formatted(proj.getId(), LocalDate.now())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.jobDescription").value("Need a Java developer with AWS experience"));
    }

    @Test
    void updatePosition_updatesJobDescription() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var created = mockMvc.perform(post("/api/v1/positions").contentType(MediaType.APPLICATION_JSON)
                        .content(positionJson(proj.getId())))
                .andExpect(status().isCreated()).andReturn();
        long id = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(put("/api/v1/positions/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Lead Java Developer","projectId":%d,"requiredSkill":"Java",
                                 "billable":true,"allocationPercent":50,"status":"OPEN",
                                 "jobDescription":"Updated description"}"""
                                .formatted(proj.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobDescription").value("Updated description"));
    }
}
