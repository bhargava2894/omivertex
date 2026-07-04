package com.softility.omivertex.api;

import com.softility.omivertex.domain.Proficiency;
import com.softility.omivertex.domain.WorkMode;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SkillReportApiTest extends ApiTestBase {

    @Test
    void getSkillReport_returnsProficiencyCounts() throws Exception {
        var priya = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        var rahul = associate("Rahul Verma", "rahul@softility.com", WorkMode.ONSHORE);
        var anita = associate("Anita Rao", "anita@softility.com", WorkMode.OFFSHORE);

        var db = skill("Database", "PostgreSQL");
        var cloud = skill("Cloud", "AWS");

        rateSkill(priya, db, Proficiency.MASTERY);
        rateSkill(rahul, db, Proficiency.MASTERY);
        rateSkill(anita, cloud, Proficiency.INTERMEDIATE);

        mockMvc.perform(get("/api/v1/reports/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.category=='Database')].skills", hasSize(1)))
                .andExpect(jsonPath("$[?(@.category=='Database')].skills[0].skill").value("PostgreSQL"))
                .andExpect(jsonPath("$[?(@.category=='Database')].skills[0].counts.MASTERY").value(2))
                .andExpect(jsonPath("$[?(@.category=='Database')].skills[0].counts.NOVICE").value(0))
                .andExpect(jsonPath("$[?(@.category=='Cloud')].skills[0].skill").value("AWS"))
                .andExpect(jsonPath("$[?(@.category=='Cloud')].skills[0].counts.INTERMEDIATE").value(1));
    }

    @Test
    void getSkillReport_listsThePeopleBehindEachBar() throws Exception {
        var priya = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        var rahul = associate("Rahul Verma", "rahul@softility.com", WorkMode.ONSHORE);
        var anita = associate("Anita Rao", "anita@softility.com", WorkMode.OFFSHORE);

        var db = skill("Database", "PostgreSQL");
        rateSkill(priya, db, Proficiency.MASTERY);
        rateSkill(rahul, db, Proficiency.FOUNDATIONAL);
        rateSkill(anita, db, Proficiency.MASTERY);

        mockMvc.perform(get("/api/v1/reports/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].skills[0].people", hasSize(3)))
                // sorted highest proficiency first, then by name
                .andExpect(jsonPath("$[0].skills[0].people[0].name").value("Anita Rao"))
                .andExpect(jsonPath("$[0].skills[0].people[0].proficiency").value("MASTERY"))
                .andExpect(jsonPath("$[0].skills[0].people[0].associateId").isNumber())
                .andExpect(jsonPath("$[0].skills[0].people[1].name").value("Priya Sharma"))
                .andExpect(jsonPath("$[0].skills[0].people[2].name").value("Rahul Verma"))
                .andExpect(jsonPath("$[0].skills[0].people[2].proficiency").value("FOUNDATIONAL"));
    }
}
