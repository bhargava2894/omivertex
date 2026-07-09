package com.softility.omivertex.service;

import com.softility.omivertex.domain.Skill;
import com.softility.omivertex.repository.SkillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class ResumeSkillMatcherTest {

    private SkillRepository skillRepository;
    private ResumeSkillMatcher matcher;

    private Skill java;
    private Skill js;
    private Skill cpp;
    private Skill net;

    @BeforeEach
    void setUp() {
        skillRepository = Mockito.mock(SkillRepository.class);
        matcher = new ResumeSkillMatcher(skillRepository);

        java = new Skill();
        java.setId(1L);
        java.setName("Java");

        js = new Skill();
        js.setId(2L);
        js.setName("JavaScript");

        cpp = new Skill();
        cpp.setId(3L);
        cpp.setName("C++");

        net = new Skill();
        net.setId(4L);
        net.setName(".NET");

        when(skillRepository.findAll()).thenReturn(List.of(java, js, cpp, net));
    }

    @Test
    void matchSkills_matchesStandardSkillsCaseInsensitively() {
        String text = "I am a java programmer experienced in .net development.";
        List<Skill> matches = matcher.matchSkills(text);

        assertEquals(2, matches.size());
        assertTrue(matches.contains(java));
        assertTrue(matches.contains(net));
    }

    @Test
    void matchSkills_usesWordBoundariesToPreventSubstrMatching() {
        String text = "I write code in JavaScript and C++.";
        List<Skill> matches = matcher.matchSkills(text);

        // Should match JavaScript and C++, but NOT Java since word boundary \bJava\b won't match JavaScript
        assertEquals(2, matches.size());
        assertTrue(matches.contains(js));
        assertTrue(matches.contains(cpp));
        assertFalse(matches.contains(java));
    }

    @Test
    void matchSkills_handlesSpecialCharactersCorrectly() {
        String text = "Skills: C++, C#, and .NET Core.";
        List<Skill> matches = matcher.matchSkills(text);

        // cpp and net should match
        assertTrue(matches.contains(cpp));
        assertTrue(matches.contains(net));
    }

    @Test
    void matchSkills_returnsEmptyForBlankText() {
        assertTrue(matcher.matchSkills("").isEmpty());
        assertTrue(matcher.matchSkills(null).isEmpty());
    }
}
