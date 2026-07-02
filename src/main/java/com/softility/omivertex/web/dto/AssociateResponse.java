package com.softility.omivertex.web.dto;

import com.softility.omivertex.domain.Allocation;
import com.softility.omivertex.domain.Associate;
import com.softility.omivertex.domain.EntityStatus;
import com.softility.omivertex.domain.WorkMode;

import java.util.List;

public record AssociateResponse(
        Long id,
        String name,
        String email,
        String company,
        String location,
        WorkMode workMode,
        String designation,
        EntityStatus status,
        boolean billable,
        Long currentProjectId,
        String currentProject,
        String currentClient) {

    /** Builds the response from the associate plus their current (open, started) allocations. */
    public static AssociateResponse from(Associate associate, List<Allocation> currentAllocations) {
        boolean billable = currentAllocations.stream().anyMatch(Allocation::isBillable);
        Allocation primary = currentAllocations.stream()
                .filter(Allocation::isBillable).findFirst()
                .orElse(currentAllocations.isEmpty() ? null : currentAllocations.get(0));
        return new AssociateResponse(associate.getId(), associate.getName(), associate.getEmail(),
                associate.getCompany(), associate.getLocation(), associate.getWorkMode(),
                associate.getDesignation(), associate.getStatus(), billable,
                primary == null ? null : primary.getProject().getId(),
                primary == null ? null : primary.getProject().getName(),
                primary == null ? null : primary.getProject().getClient().getName());
    }
}
