package com.softility.omivertex.api;

import com.softility.omivertex.domain.WorkMode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DashboardApiTest extends ApiTestBase {

    @Test
    void summary_returnsResourceKpis() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var billableDev = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        var shadowDev = associate("Rahul Verma", "rahul@softility.com", WorkMode.ONSHORE);
        associate("Anita Rao", "anita@softility.com", WorkMode.ONSHORE);
        allocation(billableDev, proj, true);
        var rollingOff = allocation(shadowDev, proj, false);
        rollingOff.setEndDate(LocalDate.now().plusDays(10));
        allocationRepository.save(rollingOff);

        // Certs setup
        cert(billableDev, "AWS Certified Solutions Architect", LocalDate.now().plusDays(25));
        cert(shadowDev, "Certified Scrum Master", LocalDate.now().plusDays(200));

        mockMvc.perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAssociates").value(3))
                .andExpect(jsonPath("$.billableCount").value(1))
                .andExpect(jsonPath("$.nonBillableCount").value(1))
                .andExpect(jsonPath("$.benchCount").value(1))
                .andExpect(jsonPath("$.onshoreCount").value(2))
                .andExpect(jsonPath("$.offshoreCount").value(1))
                .andExpect(jsonPath("$.totalClients").value(1))
                .andExpect(jsonPath("$.activeProjects").value(1))
                .andExpect(jsonPath("$.clientHeadcounts", hasSize(1)))
                .andExpect(jsonPath("$.clientHeadcounts[0].clientName").value("Acme Corp"))
                .andExpect(jsonPath("$.clientHeadcounts[0].headcount").value(2))
                // allocations in ApiTestBase start 3 months ago, so the last 4 points
                // of the 6-month trend carry them and the first 2 are empty
                .andExpect(jsonPath("$.staffingTrend", hasSize(6)))
                .andExpect(jsonPath("$.staffingTrend[0].total").value(0))
                .andExpect(jsonPath("$.staffingTrend[5].total").value(2))
                .andExpect(jsonPath("$.staffingTrend[5].billable").value(1))
                .andExpect(jsonPath("$.staffingTrend[5].month").isNotEmpty())
                // 1 associate fully billable of 3 total -> 33% FTE-weighted utilization
                .andExpect(jsonPath("$.utilizationPercent").value(33))
                // Anita is fresh on the bench (created today)
                .andExpect(jsonPath("$.benchAging.days0to30").value(1))
                .andExpect(jsonPath("$.benchAging.days31to60").value(0))
                .andExpect(jsonPath("$.benchAging.days60plus").value(0))
                .andExpect(jsonPath("$.benchAssociates", hasSize(1)))
                .andExpect(jsonPath("$.benchAssociates[0].name").value("Anita Rao"))
                .andExpect(jsonPath("$.benchAssociates[0].benchDays").value(0))
                // Rahul's allocation ends in 10 days -> roll-off radar
                .andExpect(jsonPath("$.upcomingRolloffs", hasSize(1)))
                .andExpect(jsonPath("$.upcomingRolloffs[0].associateName").value("Rahul Verma"))
                .andExpect(jsonPath("$.upcomingRolloffs[0].projectName").value("Storefront Revamp"))
                .andExpect(jsonPath("$.upcomingRolloffs[0].daysLeft").value(10))
                .andExpect(jsonPath("$.openPositions").value(0))
                .andExpect(jsonPath("$.expiringCertifications", hasSize(1)))
                .andExpect(jsonPath("$.expiringCertifications[0].associateName").value("Priya Sharma"))
                .andExpect(jsonPath("$.expiringCertifications[0].name").value("AWS Certified Solutions Architect"))
                .andExpect(jsonPath("$.expiringCertifications[0].daysLeft").value(25));
    }

    @Test
    void clientHeadcounts_splitBillableAndNonBillable() throws Exception {
        var acme = client("Acme Corp");
        var p1 = project("ACM-1", "Data Platform", acme);
        var p2 = project("ACM-2", "Support Desk", acme);
        var dev = associate("Asha Iyer", "asha@softility.com", WorkMode.OFFSHORE);
        var qa = associate("Vikram Das", "vikram@softility.com", WorkMode.OFFSHORE);
        allocation(dev, p1, true);   // billable on Acme
        allocation(qa, p2, false);   // non-billable on Acme

        mockMvc.perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientHeadcounts[0].clientName").value("Acme Corp"))
                .andExpect(jsonPath("$.clientHeadcounts[0].clientId").value(acme.getId()))
                .andExpect(jsonPath("$.clientHeadcounts[0].headcount").value(2))
                .andExpect(jsonPath("$.clientHeadcounts[0].billable").value(1))
                .andExpect(jsonPath("$.clientHeadcounts[0].nonBillable").value(1));
    }

    @Test
    void summary_excludesInactiveAssociatesFromKpis() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var active = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        allocation(active, proj, true);

        // a leaver: inactive, never rolled off properly — must not distort any KPI
        var leaver = associate("Gone Guy", "gone@softility.com", WorkMode.ONSHORE);
        leaver.setStatus(com.softility.omivertex.domain.EntityStatus.INACTIVE);
        associateRepository.save(leaver);

        // an inactive associate with a lingering open allocation must not count as staffed
        var leaverStaffed = associate("Left Behind", "left@softility.com", WorkMode.ONSHORE);
        allocation(leaverStaffed, proj, true);
        leaverStaffed.setStatus(com.softility.omivertex.domain.EntityStatus.INACTIVE);
        associateRepository.save(leaverStaffed);

        mockMvc.perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAssociates").value(1))
                .andExpect(jsonPath("$.billableCount").value(1))
                .andExpect(jsonPath("$.benchCount").value(0))
                .andExpect(jsonPath("$.onshoreCount").value(0))
                .andExpect(jsonPath("$.offshoreCount").value(1))
                // 1 fully billable of 1 active associate -> 100%, not diluted by leavers
                .andExpect(jsonPath("$.utilizationPercent").value(100))
                .andExpect(jsonPath("$.benchAssociates", hasSize(0)))
                .andExpect(jsonPath("$.clientHeadcounts[0].headcount").value(1));
    }

    @Test
    void summary_countsExitsInTrailingYear() throws Exception {
        var recent = associate("Gone Recently", "gone1@softility.com", WorkMode.ONSHORE);
        recent.setExitReason(com.softility.omivertex.domain.ExitReason.RESIGNED);
        recent.setLastWorkingDay(java.time.LocalDate.now().minusDays(30));
        recent.setStatus(com.softility.omivertex.domain.EntityStatus.INACTIVE);
        associateRepository.save(recent);

        var old = associate("Gone Long Ago", "gone2@softility.com", WorkMode.ONSHORE);
        old.setExitReason(com.softility.omivertex.domain.ExitReason.RESIGNED);
        old.setLastWorkingDay(java.time.LocalDate.now().minusDays(400));
        old.setStatus(com.softility.omivertex.domain.EntityStatus.INACTIVE);
        associateRepository.save(old);

        mockMvc.perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitsLast12Months").value(1));
    }

    @Test
    void summary_reportsSkillGaps() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var java = skill("Backend", "Java");

        // demand: one open position must-have Java >= INTERMEDIATE
        var position = new com.softility.omivertex.domain.OpenPosition();
        position.setTitle("Java Dev");
        position.setProject(proj);
        openPositionRepository.save(position);
        var req = new com.softility.omivertex.domain.PositionSkill();
        req.setPosition(position);
        req.setSkill(java);
        req.setMinProficiency(com.softility.omivertex.domain.Proficiency.INTERMEDIATE);
        req.setRequired(true);
        positionSkillRepository.save(req);

        // supply: one on bench with Java ADVANCE, one allocated with Java MASTERY,
        // one on bench below the required minimum
        var bench = associate("Priya Sharma", "priya@softility.com", WorkMode.ONSHORE);
        rateSkill(bench, java, com.softility.omivertex.domain.Proficiency.ADVANCE);
        var busy = associate("Rahul Verma", "rahul@softility.com", WorkMode.ONSHORE);
        rateSkill(busy, java, com.softility.omivertex.domain.Proficiency.MASTERY);
        allocation(busy, proj, true);
        var novice = associate("Anita Rao", "anita@softility.com", WorkMode.ONSHORE);
        rateSkill(novice, java, com.softility.omivertex.domain.Proficiency.NOVICE);

        mockMvc.perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skillGaps", hasSize(1)))
                .andExpect(jsonPath("$.skillGaps[0].skillName").value("Java"))
                .andExpect(jsonPath("$.skillGaps[0].demand").value(1))
                .andExpect(jsonPath("$.skillGaps[0].benchSupply").value(1))
                .andExpect(jsonPath("$.skillGaps[0].totalSupply").value(2))
                .andExpect(jsonPath("$.skillGaps[0].gap").value(0));
    }

    @Test
    void summary_forecastsUtilizationFromKnownEndDates() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var stays = associate("Priya Sharma", "priya@softility.com", WorkMode.ONSHORE);
        allocation(stays, proj, true); // open-ended billable
        var rollsOff = associate("Rahul Verma", "rahul@softility.com", WorkMode.ONSHORE);
        var ending = allocation(rollsOff, proj, true);
        ending.setEndDate(java.time.LocalDate.now().plusDays(45));
        allocationRepository.save(ending);

        mockMvc.perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.utilizationForecast", hasSize(4)))
                .andExpect(jsonPath("$.utilizationForecast[0].label").value("Today"))
                .andExpect(jsonPath("$.utilizationForecast[0].percent").value(100))
                .andExpect(jsonPath("$.utilizationForecast[1].percent").value(100)) // +30d
                .andExpect(jsonPath("$.utilizationForecast[2].percent").value(50)) // +60d
                .andExpect(jsonPath("$.utilizationForecast[3].percent").value(50)); // +90d
    }

    @Test
    void summary_withNoData_returnsZeros() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAssociates").value(0))
                .andExpect(jsonPath("$.benchCount").value(0))
                .andExpect(jsonPath("$.clientHeadcounts", hasSize(0)))
                .andExpect(jsonPath("$.expiringCertifications", hasSize(0)));
    }

    private com.softility.omivertex.domain.Certification cert(
            com.softility.omivertex.domain.Associate associate, String name, LocalDate expiry) {
        var c = new com.softility.omivertex.domain.Certification();
        c.setAssociate(associate);
        c.setName(name);
        c.setAuthority("Amazon");
        c.setIssuedDate(LocalDate.now().minusYears(1));
        c.setExpiryDate(expiry);
        return certificationRepository.save(c);
    }
}
