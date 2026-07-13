package com.softility.omivertex.api;

import com.softility.omivertex.domain.Allocation;
import com.softility.omivertex.domain.Associate;
import com.softility.omivertex.domain.EntityStatus;
import com.softility.omivertex.domain.OpenPosition;
import com.softility.omivertex.domain.PositionSkill;
import com.softility.omivertex.domain.Proficiency;
import com.softility.omivertex.domain.Project;
import com.softility.omivertex.domain.Skill;
import com.softility.omivertex.domain.WorkMode;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The drill-down behind a skill-gap row: who is asking, who is free, who is coming free, who could learn it. */
class SkillGapDetailApiTest extends ApiTestBase {

    private OpenPosition demand(String title, Skill skill, Project project, int headcount, Proficiency min) {
        OpenPosition position = new OpenPosition();
        position.setTitle(title);
        position.setProject(project);
        position.setHeadcount(headcount);
        position.setStartDate(LocalDate.now().plusDays(30));
        openPositionRepository.save(position);
        PositionSkill required = new PositionSkill();
        required.setPosition(position);
        required.setSkill(skill);
        required.setMinProficiency(min);
        required.setRequired(true);
        positionSkillRepository.save(required);
        return position;
    }

    private Allocation allocationEnding(Associate associate, Project project, LocalDate endDate) {
        Allocation allocation = allocation(associate, project, true);
        allocation.setEndDate(endDate);
        return allocationRepository.save(allocation);
    }

    @Test
    void detail_itemizesOpenDemandAndTheBenchAndNearMissCandidates() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var kubernetes = skill("Cloud", "Kubernetes");

        demand("Platform Engineer", kubernetes, proj, 2, Proficiency.ADVANCE);

        var free = associate("Priya Sharma", "priya@softility.com", WorkMode.ONSHORE);
        rateSkill(free, kubernetes, Proficiency.MASTERY); // bench, above threshold

        var trainable = associate("Mark Davis", "mark@softility.com", WorkMode.ONSHORE);
        rateSkill(trainable, kubernetes, Proficiency.FUNCTIONAL_USER); // exactly one level below ADVANCE

        var tooFarBehind = associate("Pooja Reddy", "pooja@softility.com", WorkMode.OFFSHORE);
        rateSkill(tooFarBehind, kubernetes, Proficiency.FOUNDATIONAL); // two levels below -> not a near miss

        mockMvc.perform(get("/api/v1/reports/skill-gaps/" + kubernetes.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skillName").value("Kubernetes"))
                .andExpect(jsonPath("$.category").value("Cloud"))
                .andExpect(jsonPath("$.threshold").value("ADVANCE"))
                .andExpect(jsonPath("$.openDemand", hasSize(1)))
                .andExpect(jsonPath("$.openDemand[0].title").value("Platform Engineer"))
                .andExpect(jsonPath("$.openDemand[0].projectName").value("Storefront Revamp"))
                .andExpect(jsonPath("$.openDemand[0].clientName").value("Acme Corp"))
                .andExpect(jsonPath("$.openDemand[0].headcount").value(2))
                .andExpect(jsonPath("$.openDemand[0].minProficiency").value("ADVANCE"))
                .andExpect(jsonPath("$.benchSupply", hasSize(1)))
                .andExpect(jsonPath("$.benchSupply[0].name").value("Priya Sharma"))
                .andExpect(jsonPath("$.benchSupply[0].proficiency").value("MASTERY"))
                .andExpect(jsonPath("$.rollingOff", hasSize(0)))
                .andExpect(jsonPath("$.nearMiss", hasSize(1)))
                .andExpect(jsonPath("$.nearMiss[0].name").value("Mark Davis"))
                .andExpect(jsonPath("$.nearMiss[0].proficiency").value("FUNCTIONAL_USER"))
                .andExpect(jsonPath("$.nearMiss[0].requiredProficiency").value("ADVANCE"));
    }

    @Test
    void detail_listsAllocatedHoldersRollingOffWithinTheHorizonOnly() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var kubernetes = skill("Cloud", "Kubernetes");
        demand("Platform Engineer", kubernetes, proj, 1, Proficiency.INTERMEDIATE);

        var soon = associate("Tom Wilson", "tom@softility.com", WorkMode.ONSHORE);
        rateSkill(soon, kubernetes, Proficiency.ADVANCE);
        allocationEnding(soon, proj, LocalDate.now().plusDays(30)); // last day inside the horizon

        var later = associate("Sanjay Gupta", "sanjay@softility.com", WorkMode.OFFSHORE);
        rateSkill(later, kubernetes, Proficiency.ADVANCE);
        allocationEnding(later, proj, LocalDate.now().plusDays(31)); // one day outside -> excluded

        var openEnded = associate("Arjun Patel", "arjun@softility.com", WorkMode.OFFSHORE);
        rateSkill(openEnded, kubernetes, Proficiency.MASTERY);
        allocation(openEnded, proj, true); // no end date -> not rolling off

        mockMvc.perform(get("/api/v1/reports/skill-gaps/" + kubernetes.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.benchSupply", hasSize(0)))
                .andExpect(jsonPath("$.rollingOff", hasSize(1)))
                .andExpect(jsonPath("$.rollingOff[0].name").value("Tom Wilson"))
                .andExpect(jsonPath("$.rollingOff[0].projectName").value("Storefront Revamp"))
                .andExpect(jsonPath("$.rollingOff[0].endDate").value(LocalDate.now().plusDays(30).toString()));
    }

    @Test
    void detail_forASpareSkill_namesTheIdlePeopleAndHasNoDemandOrNearMiss() throws Exception {
        var tableau = skill("Analytics", "Tableau");
        var idle = associate("Mark Davis", "mark@softility.com", WorkMode.ONSHORE);
        rateSkill(idle, tableau, Proficiency.FOUNDATIONAL);
        var leaver = associate("Old Timer", "old@softility.com", WorkMode.ONSHORE);
        rateSkill(leaver, tableau, Proficiency.MASTERY);
        leaver.setStatus(EntityStatus.INACTIVE);
        associateRepository.save(leaver);

        mockMvc.perform(get("/api/v1/reports/skill-gaps/" + tableau.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user("viewer").roles("VIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.threshold").value("NOVICE")) // no demand -> everyone qualifies
                .andExpect(jsonPath("$.openDemand", hasSize(0)))
                .andExpect(jsonPath("$.benchSupply", hasSize(1)))
                .andExpect(jsonPath("$.benchSupply[0].name").value("Mark Davis"))
                .andExpect(jsonPath("$.nearMiss", hasSize(0))); // nothing is below NOVICE
    }

    @Test
    void detail_forUnknownSkill_is404() throws Exception {
        mockMvc.perform(get("/api/v1/reports/skill-gaps/999999"))
                .andExpect(status().isNotFound());
    }
}
