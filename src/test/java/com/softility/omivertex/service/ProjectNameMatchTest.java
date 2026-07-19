package com.softility.omivertex.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProjectNameMatchTest {

    private static final List<GeminiClient.ProjectOption> OPTIONS = List.of(
            new GeminiClient.ProjectOption(1L, "Acme Corp · Storefront Revamp"),
            new GeminiClient.ProjectOption(2L, "Acme Corp · Mobile App"),
            new GeminiClient.ProjectOption(3L, "Globex · Data Platform"));

    @Test
    void matchesOnSharedTokens() {
        assertEquals(2L, PositionService.matchProjectId("Acme mobile app", OPTIONS));
        assertEquals(3L, PositionService.matchProjectId("globex data platform", OPTIONS));
    }

    @Test
    void nullWhenNoOverlap() {
        assertNull(PositionService.matchProjectId("Initech payroll", OPTIONS));
        assertNull(PositionService.matchProjectId(null, OPTIONS));
        assertNull(PositionService.matchProjectId("   ", OPTIONS));
    }
}
