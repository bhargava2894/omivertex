package com.softility.omivertex.api;

import com.softility.omivertex.domain.EntityStatus;
import com.softility.omivertex.domain.ExitReason;
import com.softility.omivertex.domain.WorkMode;
import com.softility.omivertex.service.AssociateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ExitProcessingTest extends ApiTestBase {

    @Autowired AssociateService associateService;

    @Test
    void processExits_flipsStatusAndEndsAllocations() {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var leaver = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        allocation(leaver, proj, true); // open-ended, started 3 months ago
        leaver.setExitReason(ExitReason.RESIGNED);
        leaver.setLastWorkingDay(LocalDate.now().minusDays(2));
        associateRepository.save(leaver);

        associateService.processExits();

        var refreshed = associateRepository.findById(leaver.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(EntityStatus.INACTIVE);
        var allocations = allocationRepository.findByAssociateId(leaver.getId());
        assertThat(allocations).hasSize(1);
        assertThat(allocations.get(0).getEndDate()).isEqualTo(LocalDate.now().minusDays(2));
        // idempotent: second run changes nothing and does not throw
        associateService.processExits();
    }

    @Test
    void processExits_deletesAllocationsThatNeverStarted() {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var leaver = associate("Rahul Verma", "rahul@softility.com", WorkMode.ONSHORE);
        var future = allocation(leaver, proj, true);
        future.setStartDate(LocalDate.now().plusDays(30)); // seat they will never take
        allocationRepository.save(future);
        leaver.setExitReason(ExitReason.RESIGNED);
        leaver.setLastWorkingDay(LocalDate.now().minusDays(1));
        associateRepository.save(leaver);

        associateService.processExits();

        assertThat(allocationRepository.findByAssociateId(leaver.getId())).isEmpty();
    }
}
