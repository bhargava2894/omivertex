package com.softility.omivertex.web.dto;

import java.util.List;

public record ImportSummaryResponse(
        int rowsProcessed,
        int clientsCreated,
        int projectsCreated,
        int associatesCreated,
        int allocationsCreated,
        int skipped,
        List<String> errors) {
}
