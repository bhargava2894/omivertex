package com.softility.omivertex.web;

import com.softility.omivertex.domain.AssociateSkill;
import com.softility.omivertex.domain.Proficiency;
import com.softility.omivertex.repository.AssociateSkillRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/reports/skills")
public class SkillReportController {

    private final AssociateSkillRepository associateSkillRepository;

    public SkillReportController(AssociateSkillRepository associateSkillRepository) {
        this.associateSkillRepository = associateSkillRepository;
    }

    public record SkillReportResponse(String category, List<SkillCountResponse> skills) {}

    public record SkillCountResponse(String skill, Map<Proficiency, Long> counts, List<Person> people) {}

    /** One associate behind a bar segment — lets the UI answer "who exactly?". */
    public record Person(Long associateId, String name, Proficiency proficiency) {}

    @GetMapping
    public List<SkillReportResponse> getReport() {
        List<AssociateSkill> all = associateSkillRepository.findAllWithDetails();

        // Group by category name, then by skill name, keeping alphabetical sort
        Map<String, Map<String, List<AssociateSkill>>> grouped = all.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getSkill().getCategory().getName(),
                        TreeMap::new,
                        Collectors.groupingBy(
                                s -> s.getSkill().getName(),
                                TreeMap::new,
                                Collectors.toList()
                        )
                ));

        List<SkillReportResponse> response = new ArrayList<>();
        grouped.forEach((categoryName, skillsMap) -> {
            List<SkillCountResponse> skillResponses = new ArrayList<>();
            skillsMap.forEach((skillName, associateSkills) -> {
                Map<Proficiency, Long> counts = new EnumMap<>(Proficiency.class);
                // Initialize all proficiencies with 0
                for (Proficiency p : Proficiency.values()) {
                    counts.put(p, 0L);
                }
                // Populate counts
                associateSkills.forEach(s -> counts.put(s.getProficiency(), counts.get(s.getProficiency()) + 1));
                List<Person> people = associateSkills.stream()
                        .map(s -> new Person(s.getAssociate().getId(), s.getAssociate().getName(), s.getProficiency()))
                        .sorted(Comparator.comparing(Person::proficiency, Comparator.reverseOrder())
                                .thenComparing(Person::name))
                        .toList();
                skillResponses.add(new SkillCountResponse(skillName, counts, people));
            });
            response.add(new SkillReportResponse(categoryName, skillResponses));
        });

        return response;
    }
}
