package com.softility.omivertex.api;

import com.softility.omivertex.domain.Associate;
import com.softility.omivertex.domain.Proficiency;
import com.softility.omivertex.domain.Skill;
import com.softility.omivertex.domain.WorkMode;
import com.softility.omivertex.service.AssociateService;
import com.softility.omivertex.web.dto.SkillAssignmentRequest;
import com.softility.omivertex.web.dto.SkillAssignmentRequest.Entry;
import com.softility.omivertex.web.error.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AssociateSkillDerivationTest extends ApiTestBase {

    @Autowired
    private AssociateService associateService;

    @Test
    void starredSkill_becomesPrimary_evenIfLowerProficiency() {
        Associate a = associate("Deriv One", "deriv@softility.com", WorkMode.ONSHORE);
        Skill react = skill("Frontend", "React");
        Skill node = skill("Backend", "Node.js");

        associateService.replaceSkills(a.getId(), new SkillAssignmentRequest(List.of(
                new Entry(react.getId(), Proficiency.INTERMEDIATE, true),
                new Entry(node.getId(), Proficiency.ADVANCE, false))));

        Associate saved = associateRepository.findById(a.getId()).orElseThrow();
        assertEquals("React", saved.getPrimarySkill(), "starred skill wins over higher proficiency");
        assertEquals("Node.js", saved.getSecondarySkill(), "secondary is the next-highest non-primary");
    }

    @Test
    void noStar_primaryIsHighestProficiency() {
        Associate a = associate("Deriv Two", "nostar@softility.com", WorkMode.ONSHORE);
        Skill react = skill("Frontend", "React");
        Skill node = skill("Backend", "Node.js");

        associateService.replaceSkills(a.getId(), new SkillAssignmentRequest(List.of(
                new Entry(react.getId(), Proficiency.INTERMEDIATE, false),
                new Entry(node.getId(), Proficiency.MASTERY, false))));

        Associate saved = associateRepository.findById(a.getId()).orElseThrow();
        assertEquals("Node.js", saved.getPrimarySkill());
        assertEquals("React", saved.getSecondarySkill());
    }

    @Test
    void twoPrimaries_areRejected() {
        Associate a = associate("Deriv Three", "two@softility.com", WorkMode.ONSHORE);
        Skill react = skill("Frontend", "React");
        Skill node = skill("Backend", "Node.js");

        var req = new SkillAssignmentRequest(List.of(
                new Entry(react.getId(), Proficiency.ADVANCE, true),
                new Entry(node.getId(), Proficiency.ADVANCE, true)));

        assertThrows(BadRequestException.class, () -> associateService.replaceSkills(a.getId(), req));
    }
}
