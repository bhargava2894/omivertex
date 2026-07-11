package com.softility.omivertex.api;

import com.softility.omivertex.domain.ExitReason;
import com.softility.omivertex.domain.OpenPosition;
import com.softility.omivertex.domain.PositionSkill;
import com.softility.omivertex.domain.Proficiency;
import com.softility.omivertex.domain.WorkMode;
import com.softility.omivertex.service.AssistantContextBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantContextBuilderTest extends ApiTestBase {

    @Autowired AssistantContextBuilder builder;

    private void seedWorkforce() {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var java = skill("Backend", "Java");
        var staffed = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        rateSkill(staffed, java, Proficiency.ADVANCE);
        allocation(staffed, proj, true);
        associate("Rahul Verma", "rahul@softility.com", WorkMode.ONSHORE); // bench
        var position = new OpenPosition();
        position.setTitle("Java Dev");
        position.setProject(proj);
        openPositionRepository.save(position);
        var req = new PositionSkill();
        req.setPosition(position);
        req.setSkill(java);
        req.setMinProficiency(Proficiency.INTERMEDIATE);
        req.setRequired(true);
        positionSkillRepository.save(req);
    }

    @Test
    void standingContext_containsOnlyAggregates_neverPersonalData() {
        seedWorkforce();
        String context = builder.build();

        // privacy pin: no roster data in the always-sent context
        assertThat(context).doesNotContain("Priya Sharma");
        assertThat(context).doesNotContain("priya@softility.com");
        assertThat(context).doesNotContain("Rahul Verma");
        // aggregates present
        assertThat(context).contains("Active associates: 2");
        assertThat(context).contains("Bench count: 1");
        assertThat(context).contains("Open positions: 1");
        assertThat(context).contains("Clients: 1");
        assertThat(context).contains("Projects: 1");
    }

    @Test
    void searchAssociates_filtersBySkillAndBench_capsRows() {
        seedWorkforce();
        // bench + Java at INTERMEDIATE+: nobody (Priya is allocated, Rahul unrated)
        assertThat(builder.searchAssociates(null, "Java", Proficiency.INTERMEDIATE, true))
                .contains("No matching associates");
        // Java at INTERMEDIATE+ regardless of bench: Priya
        String result = builder.searchAssociates(null, "Java", Proficiency.INTERMEDIATE, false);
        assertThat(result).contains("Priya Sharma").contains("Storefront Revamp");
        assertThat(result).doesNotContain("Rahul Verma");
    }

    @Test
    void associateDetail_showsSkillsAllocationsAndUpcomingExit() {
        seedWorkforce();
        var leaver = associateRepository.findAll().stream()
                .filter(a -> a.getName().equals("Priya Sharma")).findFirst().orElseThrow();
        leaver.setExitReason(ExitReason.RESIGNED);
        leaver.setLastWorkingDay(LocalDate.now().plusDays(10));
        associateRepository.save(leaver);

        String detail = builder.associateDetail(leaver);
        assertThat(detail).contains("Java (ADVANCE)");
        assertThat(detail).contains("Storefront Revamp @Acme Corp");
        assertThat(detail).contains("leaving on " + LocalDate.now().plusDays(10));
    }

    @Test
    void rolloffs_listsAllocationsEndingInWindow() {
        seedWorkforce();
        var alloc = allocationRepository.findAll().get(0);
        alloc.setEndDate(LocalDate.now().plusDays(10));
        allocationRepository.save(alloc);

        assertThat(builder.rolloffs(30)).contains("Priya Sharma").contains("Storefront Revamp");
        assertThat(builder.rolloffs(5)).contains("No allocations ending");
    }

    @Test
    void openPositions_listsTitleProjectAndMustHaves() {
        seedWorkforce();
        String result = builder.openPositions();
        assertThat(result).contains("Java Dev");
        assertThat(result).contains("must-have: Java (min INTERMEDIATE)");
    }
}
