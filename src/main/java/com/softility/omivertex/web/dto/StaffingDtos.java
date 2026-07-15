package com.softility.omivertex.web.dto;

import java.time.LocalDate;
import java.util.List;

/** Company → project → associates staffing tree, built from CURRENT allocations. */
public final class StaffingDtos {

    private StaffingDtos() {
    }

    public record StaffedAssociate(Long associateId, String name, String designation,
                                   int allocationPercent, boolean billable, LocalDate startDate,
                                   Long allocationId, LocalDate endDate, boolean active) {
    }

    public record StaffedProject(Long projectId, String projectName, String projectCode,
                                 long billable, long nonBillable,
                                 List<StaffedAssociate> associates) {
    }

    public record StaffedClient(Long clientId, String clientName,
                                long billable, long nonBillable,
                                List<StaffedProject> projects) {
    }
}
