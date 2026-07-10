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

    @Test
    void context_containsRosterAllocationsDemandAndKpis() {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var java = skill("Backend", "Java");

        var staffed = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        rateSkill(staffed, java, Proficiency.ADVANCE);
        allocation(staffed, proj, true);

        associate("Rahul Verma", "rahul@softility.com", WorkMode.ONSHORE);

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

        String context = builder.build();

        assertThat(context).contains("Priya Sharma");
        assertThat(context).contains("priya@softility.com"); // full-detail decision
        assertThat(context).contains("Java (ADVANCE)");
        assertThat(context).contains("Storefront Revamp");
        assertThat(context).contains("Acme Corp");
        assertThat(context).contains("Rahul Verma");
        assertThat(context).contains("BENCH"); // bench marker
        assertThat(context).contains("Java Dev");
        assertThat(context).contains("must-have: Java (min INTERMEDIATE)");
        assertThat(context).contains("Bench count: 1");
    }

    @Test
    void context_marksExitedAssociates() {
        var leaver = associate("Gone Guy", "gone@softility.com", WorkMode.ONSHORE);
        leaver.setExitReason(ExitReason.RESIGNED);
        leaver.setLastWorkingDay(LocalDate.now().plusDays(10));
        associateRepository.save(leaver);

        assertThat(builder.build()).contains("leaving on " + LocalDate.now().plusDays(10));
    }
}
