package com.softility.omivertex.web;

import com.softility.omivertex.service.SkillGapService;
import com.softility.omivertex.web.dto.DashboardSummaryResponse.SkillGap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reports/skill-gaps")
public class SkillGapController {

    private final SkillGapService skillGapService;

    public SkillGapController(SkillGapService skillGapService) {
        this.skillGapService = skillGapService;
    }

    /** Full skill supply-vs-demand report, incl. surplus skills. Admin + viewer (GET). */
    @GetMapping
    public List<SkillGap> report() {
        return skillGapService.fullReport();
    }
}
