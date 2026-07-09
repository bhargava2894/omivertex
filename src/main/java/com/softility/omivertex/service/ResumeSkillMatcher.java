package com.softility.omivertex.service;

import com.softility.omivertex.domain.Skill;
import com.softility.omivertex.repository.SkillRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class ResumeSkillMatcher {

    private final SkillRepository skillRepository;

    public ResumeSkillMatcher(SkillRepository skillRepository) {
        this.skillRepository = skillRepository;
    }

    public List<Skill> matchSkills(String text) {
        List<Skill> matched = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return matched;
        }

        List<Skill> allSkills = skillRepository.findAll();
        for (Skill skill : allSkills) {
            String skillName = skill.getName().trim();
            String patternString;
            if (skillName.equalsIgnoreCase("C++")) {
                patternString = "\\bC\\+\\+";
            } else if (skillName.equalsIgnoreCase("C#")) {
                patternString = "\\bC#";
            } else if (skillName.equalsIgnoreCase(".NET")) {
                patternString = "\\.NET\\b";
            } else {
                patternString = "\\b" + Pattern.quote(skillName) + "\\b";
            }

            Pattern pattern = Pattern.compile(patternString, Pattern.CASE_INSENSITIVE);
            if (pattern.matcher(text).find()) {
                matched.add(skill);
            }
        }
        return matched;
    }
}
