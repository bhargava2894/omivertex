package com.softility.omivertex.api;

import com.softility.omivertex.domain.OpenPosition;
import com.softility.omivertex.domain.PositionSkill;
import com.softility.omivertex.domain.Proficiency;
import com.softility.omivertex.domain.WorkMode;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SkillGapReportApiTest extends ApiTestBase {

    /** Demand seat: one OPEN position requiring the skill at the given minimum. */
    private void demand(String title, com.softility.omivertex.domain.Skill skill,
                        com.softility.omivertex.domain.Project project, Proficiency min) {
        OpenPosition position = new OpenPosition();
        position.setTitle(title);
        position.setProject(project);
        openPositionRepository.save(position);
        PositionSkill req = new PositionSkill();
        req.setPosition(position);
        req.setSkill(skill);
        req.setMinProficiency(min);
        req.setRequired(true);
        positionSkillRepository.save(req);
    }

    @Test
    void report_includesDemandAndSurplusRows_sortedWorstGapFirst() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var java = skill("Backend", "Java");
        var react = skill("Frontend", "React");

        // Java: 1 open seat, nobody qualified -> gap +1 (worst, sorts first)
        demand("Java Dev", java, proj, Proficiency.INTERMEDIATE);

        // React: no open demand, one bench holder -> surplus row, gap -1
        var bench = associate("Priya Sharma", "priya@softility.com", WorkMode.ONSHORE);
        rateSkill(bench, react, Proficiency.ADVANCE);

        mockMvc.perform(get("/api/v1/reports/skill-gaps"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].skillName").value("Java"))
                .andExpect(jsonPath("$[0].demand").value(1))
                .andExpect(jsonPath("$[0].benchSupply").value(0))
                .andExpect(jsonPath("$[0].gap").value(1))
                .andExpect(jsonPath("$[1].skillName").value("React"))
                .andExpect(jsonPath("$[1].demand").value(0))
                .andExpect(jsonPath("$[1].benchSupply").value(1))
                .andExpect(jsonPath("$[1].totalSupply").value(1))
                .andExpect(jsonPath("$[1].gap").value(-1));
    }

    @Test
    void report_isReadableByViewer() throws Exception {
        var bench = associate("Priya Sharma", "priya@softility.com", WorkMode.ONSHORE);
        rateSkill(bench, skill("Backend", "Java"), Proficiency.ADVANCE);

        mockMvc.perform(get("/api/v1/reports/skill-gaps")
                        .with(SecurityMockMvcRequestPostProcessors.user("viewer").roles("VIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void report_excludesInactiveAssociatesAndUnratedSkills() throws Exception {
        var java = skill("Backend", "Java");
        skill("Backend", "Kotlin"); // in taxonomy but never rated, no demand -> no row
        var leaver = associate("Old Timer", "old@softility.com", WorkMode.ONSHORE);
        rateSkill(leaver, java, Proficiency.MASTERY);
        leaver.setStatus(com.softility.omivertex.domain.EntityStatus.INACTIVE);
        associateRepository.save(leaver);

        mockMvc.perform(get("/api/v1/reports/skill-gaps"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1))) // Java row exists (rated), but supply is zero
                .andExpect(jsonPath("$[0].skillName").value("Java"))
                .andExpect(jsonPath("$[0].totalSupply").value(0))
                .andExpect(jsonPath("$[0].gap").value(0)); // 0 demand - 0 bench
    }
}
