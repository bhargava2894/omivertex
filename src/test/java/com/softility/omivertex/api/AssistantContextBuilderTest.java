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
    void associateDetail_showsPastProjects() {
        var acme = client("Acme Corp");
        var oldProj = project("ACM-050", "Legacy Migration", acme);
        var alum = associate("Pavan Sista", "pavan@softility.com", WorkMode.OFFSHORE);
        var ended = allocation(alum, oldProj, true);
        ended.setStartDate(LocalDate.now().minusMonths(6));
        ended.setEndDate(LocalDate.now().minusMonths(1)); // ended last month
        allocationRepository.save(ended);

        String detail = builder.associateDetail(alum);

        // the ended allocation is this associate's project history — it must surface
        assertThat(detail).contains("past projects");
        assertThat(detail).contains("Legacy Migration @Acme Corp");
    }

    @Test
    void associateDetail_showsCertifications() {
        var holder = associate("Ravi Kumar", "ravi@softility.com", WorkMode.OFFSHORE);
        var cert = new com.softility.omivertex.domain.Certification();
        cert.setAssociate(holder);
        cert.setName("AWS Solutions Architect");
        cert.setAuthority("Amazon");
        cert.setExpiryDate(LocalDate.now().plusYears(1));
        certificationRepository.save(cert);

        String detail = builder.associateDetail(holder);

        // certifications are first-class data — the assistant must be able to report them
        assertThat(detail).contains("certifications");
        assertThat(detail).contains("AWS Solutions Architect (Amazon)");
    }

    @Test
    void associateDetail_currentAllocationNotListedAsPastProject() {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var neha = associate("Neha Gupta", "neha@softility.com", WorkMode.ONSHORE);
        allocation(neha, proj, true); // current: started 3mo ago, no end date

        String detail = builder.associateDetail(neha);

        assertThat(detail).contains("allocated: Storefront Revamp");
        assertThat(detail).doesNotContain("past projects"); // a current allocation is not history
    }

    @Test
    void associateDetail_marksFormerEmployeeAndKeepsHistory() {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var alum = associate("Nikhil Rao", "nikhil@softility.com", WorkMode.OFFSHORE);
        var ended = allocation(alum, proj, true);
        ended.setStartDate(LocalDate.now().minusMonths(6));
        ended.setEndDate(LocalDate.now().minusMonths(2));
        allocationRepository.save(ended);
        alum.setStatus(com.softility.omivertex.domain.EntityStatus.INACTIVE);
        alum.setLastWorkingDay(LocalDate.now().minusMonths(2));
        alum.setExitReason(ExitReason.RESIGNED);
        associateRepository.save(alum);

        String detail = builder.associateDetail(alum);

        assertThat(detail).contains("FORMER EMPLOYEE");
        assertThat(detail).contains("Storefront Revamp @Acme Corp"); // history still available
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
    void projectDetail_listsCurrentRosterAndExcludesEnded() {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var onIt = associate("Asha Nair", "asha@softility.com", WorkMode.ONSHORE);
        allocation(onIt, proj, true); // current
        var rolledOff = associate("Vikram Singh", "vikram@softility.com", WorkMode.OFFSHORE);
        var ended = allocation(rolledOff, proj, true);
        ended.setEndDate(LocalDate.now().minusDays(5));
        allocationRepository.save(ended);

        String detail = builder.projectDetail(proj);

        assertThat(detail).contains("Storefront Revamp").contains("Acme Corp");
        assertThat(detail).contains("Asha Nair"); // currently allocated
        assertThat(detail).doesNotContain("Vikram Singh"); // rolled off — not current roster
    }

    @Test
    void projectDetail_emptyRosterIsStated() {
        var acme = client("Acme Corp");
        var proj = project("ACM-200", "Fresh Start", acme);

        assertThat(builder.projectDetail(proj)).contains("No one is currently allocated");
    }

    @Test
    void openPositions_listsTitleProjectAndMustHaves() {
        seedWorkforce();
        String result = builder.openPositions();
        assertThat(result).contains("Java Dev");
        assertThat(result).contains("must-have: Java (min INTERMEDIATE)");
    }
}
