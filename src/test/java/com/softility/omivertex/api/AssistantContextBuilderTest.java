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
    void standingContext_mentionsMirai() {
        seedWorkforce();
        assertThat(builder.build()).contains("Mirai");
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

    @Test
    void standingContext_advertisesTheClientAndProjectTools() {
        seedWorkforce();
        // an unnamed tool is an under-called tool — the prompt lists what Mirai can reach
        assertThat(builder.build()).contains("list_clients").contains("list_projects");
    }

    @Test
    void listClients_namesEveryClient_notJustOnesWithOpenPositions() {
        seedWorkforce(); // Acme Corp — has a project and an open position
        var quiet = client("Quiet Holdings"); // no projects, no positions, no allocations
        quiet.setIndustry("Finance");
        quiet.setLocation("Zurich");
        clientRepository.save(quiet);

        String result = builder.listClients();

        // the whole point: a client reachable through no other tool still gets named
        assertThat(result).contains("Quiet Holdings");
        assertThat(result).contains("Finance").contains("Zurich");
        assertThat(result).contains("Acme Corp");
        assertThat(result).contains("1 project"); // Acme's project count
        assertThat(result).contains("no projects"); // Quiet Holdings'
    }

    @Test
    void listClients_marksInactiveSoTheListReconcilesWithTheCount() {
        var gone = client("Former Client");
        gone.setStatus(com.softility.omivertex.domain.EntityStatus.INACTIVE);
        clientRepository.save(gone);

        // build()'s "Clients:" count is every row, so the list must be every row too
        assertThat(builder.listClients()).contains("Former Client").contains("INACTIVE");
    }

    @Test
    void listProjects_listsCodeClientAndStatus_andFiltersByClient() {
        seedWorkforce(); // Storefront Revamp @Acme Corp
        var other = client("Helios Energy");
        project("HEL-100", "Grid Analytics", other);

        String all = builder.listProjects(null);
        assertThat(all).contains("Storefront Revamp").contains("ACM-100").contains("Acme Corp");
        assertThat(all).contains("Grid Analytics");

        String helios = builder.listProjects("helios"); // partial, case-insensitive
        assertThat(helios).contains("Grid Analytics");
        assertThat(helios).doesNotContain("Storefront Revamp");
    }

    @Test
    void listProjects_unknownClientIsStated() {
        seedWorkforce();
        assertThat(builder.listProjects("Nonexistent Inc")).contains("No matching projects");
    }

    @Test
    void skillGaps_reportsDemandBenchSupplyHoldersAndGap() {
        seedWorkforce();
        String result = builder.skillGaps();
        assertThat(result).contains("Java (Backend)");
        assertThat(result).contains("demand 1");
        assertThat(result).contains("bench supply 0"); // Priya is allocated; Rahul is unrated
        assertThat(result).contains("total holders 1");
        assertThat(result).contains("gap 1");
    }

    @Test
    void skillGaps_emptyWhenNoOpenDemand() {
        associate("Rahul Verma", "rahul@softility.com", WorkMode.ONSHORE); // no positions at all
        assertThat(builder.skillGaps()).contains("No open positions demand any skills");
    }

    @Test
    void standingContext_advertisesSkillGapTool() {
        seedWorkforce();
        assertThat(builder.build()).contains("get_skill_gaps");
    }

    @Test
    void expiringCertifications_windowIsInclusive_excludesBeyondAndExpired() {
        var holder = associate("Ravi Kumar", "ravi@softility.com", WorkMode.OFFSHORE);
        certification(holder, "AWS Solutions Architect", LocalDate.now().plusDays(60)); // boundary day
        certification(holder, "Azure Administrator", LocalDate.now().plusDays(61));     // beyond window
        certification(holder, "GCP Engineer", LocalDate.now().minusDays(1));            // already expired

        String result = builder.expiringCertifications(60);

        assertThat(result).contains("Ravi Kumar — AWS Solutions Architect");
        assertThat(result).contains("(in 60 days)");
        assertThat(result).doesNotContain("Azure Administrator");
        assertThat(result).doesNotContain("GCP Engineer");
    }

    @Test
    void expiringCertifications_emptyStateNamesTheWindow() {
        assertThat(builder.expiringCertifications(30))
                .contains("No certifications expire within 30 days");
    }

    @Test
    void expiringCertifications_capsRowsWithOverflowLine() {
        var holder = associate("Ravi Kumar", "ravi@softility.com", WorkMode.OFFSHORE);
        for (int i = 1; i <= 27; i++) {
            certification(holder, "Cert " + i, LocalDate.now().plusDays(i));
        }
        // 27 upcoming certs, MAX_TOOL_ROWS = 25 -> shared overflow line
        assertThat(builder.expiringCertifications(90)).contains("…and 2 more");
    }

    @Test
    void standingContext_advertisesCertExpiryTool() {
        seedWorkforce();
        assertThat(builder.build()).contains("list_expiring_certifications");
    }

    private void certification(com.softility.omivertex.domain.Associate holder, String name,
                               LocalDate expiry) {
        var cert = new com.softility.omivertex.domain.Certification();
        cert.setAssociate(holder);
        cert.setName(name);
        cert.setExpiryDate(expiry);
        certificationRepository.save(cert);
    }

    @Test
    void workforceSummary_coversKpisBucketsTrendAndForecast() {
        seedWorkforce(); // Priya 100% billable, Rahul benched -> 2 active, utilization 50%
        var alloc = allocationRepository.findAll().get(0);
        alloc.setEndDate(LocalDate.now().plusDays(10)); // roll-off inside the +30d forecast window
        allocationRepository.save(alloc);

        String result = builder.workforceSummary();

        assertThat(result).contains("Active associates: 2");
        assertThat(result).contains("bench 1");
        assertThat(result).contains("utilization: 50%");
        assertThat(result).contains("Bench aging: 1 ≤30d");
        assertThat(result).contains("Staffing trend");
        assertThat(result).contains("Utilization forecast");
        assertThat(result).contains("Today: 50%");
        assertThat(result).contains("(-50 vs today)"); // the +30d point dropped to 0%
        // the scheduled roll-off surfaces as a named forecast driver
        assertThat(result).contains("ROLL_OFF Priya Sharma (Storefront Revamp)");
    }

    @Test
    void workforceSummary_leavesSlicesToTheirOwnTools() {
        seedWorkforce();
        String result = builder.workforceSummary();
        // open-position, cert, and skill-gap rows belong to their dedicated tools
        assertThat(result).doesNotContain("Java Dev");
        assertThat(result).doesNotContain("must-have");
        assertThat(result).doesNotContain("gap ");
    }

    @Test
    void standingContext_advertisesWorkforceSummaryTool() {
        seedWorkforce();
        assertThat(builder.build()).contains("get_workforce_summary");
    }

    @Test
    void benchAging_bucketHeadline_longestBenchedFirst() {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var longBench = associate("Anil Kumar", "anil@softility.com", WorkMode.OFFSHORE);
        var endedLongAgo = allocation(longBench, proj, true);
        endedLongAgo.setStartDate(LocalDate.now().minusDays(200));
        endedLongAgo.setEndDate(LocalDate.now().minusDays(74)); // 74 days on bench
        allocationRepository.save(endedLongAgo);
        var freshBench = associate("Meera Iyer", "meera@softility.com", WorkMode.ONSHORE);
        var endedRecently = allocation(freshBench, proj, true);
        endedRecently.setStartDate(LocalDate.now().minusDays(60));
        endedRecently.setEndDate(LocalDate.now().minusDays(5)); // 5 days on bench
        allocationRepository.save(endedRecently);

        String result = builder.benchAging();

        assertThat(result).contains("1 ≤30d").contains("0 31–60d").contains("1 >60d");
        assertThat(result).contains("74 days on bench");
        assertThat(result.indexOf("Anil Kumar")).isLessThan(result.indexOf("Meera Iyer"));
    }

    @Test
    void benchAging_emptyWhenEveryoneIsAllocated() {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var busy = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        allocation(busy, proj, true);

        assertThat(builder.benchAging()).contains("No one is on the bench");
    }

    @Test
    void benchAging_capsRowsWithOverflowLine() {
        for (int i = 1; i <= 27; i++) {
            associate("Bench Person " + i, "bench" + i + "@softility.com", WorkMode.OFFSHORE);
        }
        // 27 benched people, MAX_TOOL_ROWS = 25 -> shared overflow line
        assertThat(builder.benchAging()).contains("…and 2 more");
    }

    @Test
    void standingContext_advertisesBenchAgingTool() {
        seedWorkforce();
        assertThat(builder.build()).contains("list_bench_aging");
    }
}
