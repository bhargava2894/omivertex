package com.softility.omivertex.web;

import com.softility.omivertex.domain.WorkMode;
import com.softility.omivertex.service.AssociateService;
import com.softility.omivertex.web.dto.AssociateRequest;
import com.softility.omivertex.web.dto.AssociateResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/associates")
public class AssociateController {

    private final AssociateService associateService;

    public AssociateController(AssociateService associateService) {
        this.associateService = associateService;
    }

    @GetMapping
    public List<AssociateResponse> list(@RequestParam(required = false) WorkMode workMode,
                                        @RequestParam(required = false) Boolean billable,
                                        @RequestParam(required = false) Boolean bench) {
        return associateService.list(workMode, billable, bench);
    }

    @GetMapping("/{id}")
    public AssociateResponse get(@PathVariable Long id) {
        return associateService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AssociateResponse create(@Valid @RequestBody AssociateRequest request) {
        return associateService.create(request);
    }

    @PutMapping("/{id}")
    public AssociateResponse update(@PathVariable Long id, @Valid @RequestBody AssociateRequest request) {
        return associateService.update(id, request);
    }

    @PutMapping("/{id}/skills")
    public AssociateResponse replaceSkills(@PathVariable Long id,
                                           @Valid @RequestBody com.softility.omivertex.web.dto.SkillAssignmentRequest request) {
        return associateService.replaceSkills(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        associateService.delete(id);
    }
}
