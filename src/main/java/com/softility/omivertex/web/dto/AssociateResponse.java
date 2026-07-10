package com.softility.omivertex.web.dto;

import com.softility.omivertex.domain.Allocation;
import com.softility.omivertex.domain.Associate;
import com.softility.omivertex.domain.AssociateSkill;
import com.softility.omivertex.domain.EntityStatus;
import com.softility.omivertex.domain.Proficiency;
import com.softility.omivertex.domain.WorkMode;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AssociateResponse(
        Long id,
        String name,
        String email,
        String company,
        String location,
        WorkMode workMode,
        String designation,
        LocalDate joinedDate,
        String primarySkill,
        String secondarySkill,
        EntityStatus status,
        boolean billable,
        Long currentProjectId,
        String currentProject,
        String currentClient,
        Long benchDays,
        List<SkillGroup> skillGroups,
        String resumeFilename) {

    public record RatedSkill(Long skillId, String name, Proficiency proficiency, boolean primary) {
    }

    public record SkillGroup(String category, List<RatedSkill> skills) {
    }

    /** Builds the response from the associate plus their full allocation history and rated skills. */
    public static AssociateResponse from(Associate associate, List<Allocation> allocations,
                                         List<AssociateSkill> ratedSkills) {
        return from(associate, allocations, ratedSkills, null);
    }

    /** Builds the response from the associate plus allocations, rated skills, and résumé filename. */
    public static AssociateResponse from(Associate associate, List<Allocation> allocations,
                                         List<AssociateSkill> ratedSkills, String resumeFilename) {
        List<Allocation> current = allocations.stream().filter(Allocation::isCurrent).toList();
        boolean billable = current.stream().anyMatch(Allocation::isBillable);
        Allocation primary = current.stream()
                .filter(Allocation::isBillable).findFirst()
                .orElse(current.isEmpty() ? null : current.get(0));
        return new AssociateResponse(associate.getId(), associate.getName(), associate.getEmail(),
                associate.getCompany(), associate.getLocation(), associate.getWorkMode(),
                associate.getDesignation(), associate.getJoinedDate(),
                associate.getPrimarySkill(), associate.getSecondarySkill(),
                associate.getStatus(), billable,
                primary == null ? null : primary.getProject().getId(),
                primary == null ? null : primary.getProject().getName(),
                primary == null ? null : primary.getProject().getClient().getName(),
                benchDays(associate, allocations),
                groupSkills(ratedSkills),
                resumeFilename);
    }

    /** Groups rated skills by category name, categories and skills alphabetical. */
    public static List<SkillGroup> groupSkills(List<AssociateSkill> ratedSkills) {
        Map<String, List<RatedSkill>> byCategory = new LinkedHashMap<>();
        ratedSkills.stream()
                .sorted(Comparator.comparing((AssociateSkill s) -> s.getSkill().getCategory().getName())
                        .thenComparing(s -> s.getSkill().getName()))
                .forEach(s -> byCategory
                        .computeIfAbsent(s.getSkill().getCategory().getName(), k -> new java.util.ArrayList<>())
                        .add(new RatedSkill(s.getSkill().getId(), s.getSkill().getName(),
                                s.getProficiency(), s.isPrimary())));
        return byCategory.entrySet().stream()
                .map(e -> new SkillGroup(e.getKey(), List.copyOf(e.getValue())))
                .toList();
    }

    /**
     * Days since the associate's last allocation ended (or since they joined, if never
     * allocated). Null when they hold a current allocation — they are not on the bench.
     */
    public static Long benchDays(Associate associate, List<Allocation> allocations) {
        if (allocations.stream().anyMatch(Allocation::isCurrent)) {
            return null;
        }
        LocalDate today = LocalDate.now();
        LocalDate since = allocations.stream()
                .map(Allocation::getEndDate)
                .filter(end -> end != null && end.isBefore(today))
                .max(Comparator.naturalOrder())
                .orElseGet(() -> {
                    if (associate.getJoinedDate() != null) {
                        return associate.getJoinedDate();
                    }
                    return associate.getCreatedAt() == null
                            ? today
                            : associate.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate();
                });
        return Math.max(0, ChronoUnit.DAYS.between(since, today));
    }
}
