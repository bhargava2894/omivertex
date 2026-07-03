package com.softility.omivertex.web;

import com.softility.omivertex.service.TaxonomyService;
import com.softility.omivertex.web.dto.TaxonomyDtos.CategoryRequest;
import com.softility.omivertex.web.dto.TaxonomyDtos.CategoryResponse;
import com.softility.omivertex.web.dto.TaxonomyDtos.SkillRequest;
import com.softility.omivertex.web.dto.TaxonomyDtos.SkillResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/taxonomy")
public class TaxonomyController {

    private final TaxonomyService taxonomyService;

    public TaxonomyController(TaxonomyService taxonomyService) {
        this.taxonomyService = taxonomyService;
    }

    @GetMapping
    public List<CategoryResponse> list() {
        return taxonomyService.list();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse createCategory(@Valid @RequestBody CategoryRequest request) {
        return taxonomyService.createCategory(request);
    }

    @PostMapping("/skills")
    @ResponseStatus(HttpStatus.CREATED)
    public SkillResponse createSkill(@Valid @RequestBody SkillRequest request) {
        return taxonomyService.createSkill(request);
    }

    @DeleteMapping("/skills/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSkill(@PathVariable Long id) {
        taxonomyService.deleteSkill(id);
    }

    @DeleteMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable Long id) {
        taxonomyService.deleteCategory(id);
    }
}
