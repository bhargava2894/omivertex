package com.softility.omivertex.web;

import com.softility.omivertex.service.StaffingService;
import com.softility.omivertex.web.dto.StaffingDtos.StaffedClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/staffing")
public class StaffingController {

    private final StaffingService staffingService;

    public StaffingController(StaffingService staffingService) {
        this.staffingService = staffingService;
    }

    /** Company → project → associates tree from current allocations. Admin + viewer (GET). */
    @GetMapping
    public List<StaffedClient> staffing() {
        return staffingService.staffing();
    }
}
