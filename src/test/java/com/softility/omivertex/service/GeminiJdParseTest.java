package com.softility.omivertex.service;

import com.softility.omivertex.domain.Proficiency;
import com.softility.omivertex.domain.WorkMode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GeminiJdParseTest {

    private static final List<GeminiClient.SkillOption> TAXONOMY = List.of(
            new GeminiClient.SkillOption(10L, "Java"),
            new GeminiClient.SkillOption(20L, "AWS"));

    @Test
    void mapsFieldsAndKeepsUnmatchedSkills() {
        String json = """
                {"title":"Senior Java Developer",
                 "skills":[{"skillId":10,"proficiency":"ADVANCE"},{"skillId":999,"proficiency":"NOVICE"}],
                 "unmatchedSkills":["Rust","HTMX"],
                 "jobDescription":"Build backend services.",
                 "workMode":"ONSHORE","allocationPercent":80,
                 "startDate":"2026-03-01","endDate":null,
                 "projectName":"Acme Corp · Storefront Revamp"}""";

        GeminiClient.JobDescriptionExtraction ext =
                GeminiHttpClient.parseJobDescription(json, TAXONOMY);

        assertEquals("Senior Java Developer", ext.title());
        assertEquals(1, ext.skills().size());               // skillId 999 not in taxonomy -> dropped
        assertEquals(10L, ext.skills().get(0).skillId());
        assertEquals(Proficiency.ADVANCE, ext.skills().get(0).proficiency());
        assertEquals(List.of("Rust", "HTMX"), ext.unmatchedSkills());
        assertEquals("Build backend services.", ext.jobDescriptionText());
        assertEquals(WorkMode.ONSHORE, ext.workMode());
        assertEquals(80, ext.allocationPercent());
        assertEquals(LocalDate.of(2026, 3, 1), ext.startDate());
        assertNull(ext.endDate());
        assertEquals("Acme Corp · Storefront Revamp", ext.suggestedProjectName());
    }

    @Test
    void degradesBadEnumsAndOutOfRangeAllocation() {
        String json = """
                {"title":null,"skills":[],"unmatchedSkills":[],
                 "jobDescription":null,"workMode":"HYBRID","allocationPercent":250,
                 "startDate":null,"endDate":null,"projectName":null}""";

        GeminiClient.JobDescriptionExtraction ext =
                GeminiHttpClient.parseJobDescription(json, TAXONOMY);

        assertNull(ext.title());
        assertNull(ext.workMode());          // unknown enum -> null
        assertNull(ext.allocationPercent()); // 250 out of 1..100 -> null
        assertTrue(ext.skills().isEmpty());
        assertTrue(ext.unmatchedSkills().isEmpty());
    }
}
