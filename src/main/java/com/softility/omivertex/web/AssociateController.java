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

    /**
     * Lists associates. Returns a plain array by default; when {@code page} is supplied
     * returns a {@link com.softility.omivertex.web.dto.PagedResponse} so large rosters
     * don't ship in full to the browser.
     */
    @GetMapping
    public Object list(@RequestParam(required = false) WorkMode workMode,
                       @RequestParam(required = false) Boolean billable,
                       @RequestParam(required = false) Boolean bench,
                       @RequestParam(required = false) Long categoryId,
                       @RequestParam(required = false) Long skillId,
                       @RequestParam(required = false) com.softility.omivertex.domain.Proficiency minProficiency,
                       @RequestParam(required = false) String q,
                       @RequestParam(required = false) Integer page,
                       @RequestParam(defaultValue = "25") int size) {
        List<AssociateResponse> result =
                associateService.list(workMode, billable, bench, categoryId, skillId, minProficiency);
        if (q != null && !q.isBlank()) {
            String needle = q.toLowerCase();
            result = result.stream()
                    .filter(a -> contains(a.name(), needle) || contains(a.email(), needle) || contains(a.company(), needle))
                    .toList();
        }
        return page == null ? result
                : com.softility.omivertex.web.dto.PagedResponse.of(result, page, size);
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase().contains(needle);
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
