package com.softility.omivertex.web;

import com.softility.omivertex.service.StaffingService;
import com.softility.omivertex.web.dto.StaffingDtos.StaffedClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/staffing")
public class StaffingController {

    private final StaffingService staffingService;

    public StaffingController(StaffingService staffingService) {
        this.staffingService = staffingService;
    }

    /**
     * Company → project → associates tree. Admin + viewer (GET). {@code includeEnded=true}
     * also returns non-current allocations (marked inactive) so admins can manage history;
     * counts always reflect current allocations only.
     */
    @GetMapping
    public List<StaffedClient> staffing(
            @RequestParam(name = "includeEnded", defaultValue = "false") boolean includeEnded) {
        return staffingService.staffing(includeEnded);
    }
}
