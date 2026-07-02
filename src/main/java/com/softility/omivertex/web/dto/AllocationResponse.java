package com.softility.omivertex.web.dto;

import com.softility.omivertex.domain.Allocation;

import java.time.LocalDate;

public record AllocationResponse(
        Long id,
        Long associateId,
        String associateName,
        Long projectId,
        String projectName,
        String clientName,
        boolean billable,
        int allocationPercent,
        LocalDate startDate,
        LocalDate endDate,
        boolean active) {

    public static AllocationResponse from(Allocation allocation) {
        return new AllocationResponse(allocation.getId(),
                allocation.getAssociate().getId(), allocation.getAssociate().getName(),
                allocation.getProject().getId(), allocation.getProject().getName(),
                allocation.getProject().getClient().getName(),
                allocation.isBillable(), allocation.getAllocationPercent(),
                allocation.getStartDate(), allocation.getEndDate(), allocation.isCurrent());
    }
}
