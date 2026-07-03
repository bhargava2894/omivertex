package com.softility.omivertex.service;

import com.softility.omivertex.domain.Skill;
import com.softility.omivertex.domain.SkillCategory;
import com.softility.omivertex.repository.AssociateSkillRepository;
import com.softility.omivertex.repository.SkillCategoryRepository;
import com.softility.omivertex.repository.SkillRepository;
import com.softility.omivertex.web.dto.TaxonomyDtos.CategoryRequest;
import com.softility.omivertex.web.dto.TaxonomyDtos.CategoryResponse;
import com.softility.omivertex.web.dto.TaxonomyDtos.SkillRequest;
import com.softility.omivertex.web.dto.TaxonomyDtos.SkillResponse;
import com.softility.omivertex.web.error.ConflictException;
import com.softility.omivertex.web.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class TaxonomyService {

    private final SkillCategoryRepository categories;
    private final SkillRepository skills;
    private final AssociateSkillRepository associateSkills;
    private final AuditService auditService;

    public TaxonomyService(SkillCategoryRepository categories, SkillRepository skills,
                           AssociateSkillRepository associateSkills, AuditService auditService) {
        this.categories = categories;
        this.skills = skills;
        this.associateSkills = associateSkills;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        Map<Long, List<Skill>> byCategory = skills.findAllByOrderByNameAsc().stream()
                .collect(Collectors.groupingBy(s -> s.getCategory().getId()));
        return categories.findAllByOrderByNameAsc().stream()
                .map(c -> new CategoryResponse(c.getId(), c.getName(),
                        byCategory.getOrDefault(c.getId(), List.of()).stream()
                                .map(s -> new SkillResponse(s.getId(), s.getName()))
                                .toList()))
                .toList();
    }

    public CategoryResponse createCategory(CategoryRequest request) {
        if (categories.existsByNameIgnoreCase(request.name())) {
            throw new ConflictException("Category '" + request.name() + "' already exists");
        }
        SkillCategory category = new SkillCategory();
        category.setName(request.name());
        category = categories.save(category);
        auditService.record("CREATED", "Taxonomy", category.getId(), "Added skill category " + category.getName());
        return new CategoryResponse(category.getId(), category.getName(), List.of());
    }

    public SkillResponse createSkill(SkillRequest request) {
        SkillCategory category = categories.findById(request.categoryId())
                .orElseThrow(() -> new NotFoundException("SkillCategory", request.categoryId()));
        if (skills.findByNameIgnoreCaseAndCategoryId(request.name(), category.getId()).isPresent()) {
            throw new ConflictException("Skill '" + request.name() + "' already exists in " + category.getName());
        }
        Skill skill = new Skill();
        skill.setName(request.name());
        skill.setCategory(category);
        skill = skills.save(skill);
        auditService.record("CREATED", "Taxonomy", skill.getId(),
                "Added skill " + skill.getName() + " to " + category.getName());
        return new SkillResponse(skill.getId(), skill.getName());
    }

    public void deleteSkill(Long id) {
        Skill skill = skills.findById(id).orElseThrow(() -> new NotFoundException("Skill", id));
        if (associateSkills.existsBySkillId(id)) {
            throw new ConflictException("Skill is rated on associates; remove those ratings first");
        }
        skills.delete(skill);
        auditService.record("DELETED", "Taxonomy", id, "Deleted skill " + skill.getName());
    }

    public void deleteCategory(Long id) {
        SkillCategory category = categories.findById(id)
                .orElseThrow(() -> new NotFoundException("SkillCategory", id));
        if (skills.existsByCategoryId(id)) {
            throw new ConflictException("Category still has skills; delete or move them first");
        }
        categories.delete(category);
        auditService.record("DELETED", "Taxonomy", id, "Deleted skill category " + category.getName());
    }
}
