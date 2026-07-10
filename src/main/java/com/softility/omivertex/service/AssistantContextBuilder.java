package com.softility.omivertex.service;

import com.softility.omivertex.domain.Allocation;
import com.softility.omivertex.domain.Associate;
import com.softility.omivertex.domain.AssociateSkill;
import com.softility.omivertex.domain.EntityStatus;
import com.softility.omivertex.domain.PositionSkill;
import com.softility.omivertex.domain.PositionStatus;
import com.softility.omivertex.domain.Proficiency;
import com.softility.omivertex.repository.AllocationRepository;
import com.softility.omivertex.repository.AssociateRepository;
import com.softility.omivertex.repository.AssociateSkillRepository;
import com.softility.omivertex.repository.OpenPositionRepository;
import com.softility.omivertex.repository.PositionSkillRepository;
import com.softility.omivertex.web.dto.AssociateResponse;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Compiles the live workforce picture into a dense Markdown context for the AI
 * assistant. FULL detail by user decision (2026-07-10, see docs/TODO.md):
 * names, emails, skills, allocations, exits, demand. Resume file contents are
 * never included.
 */
@Component
public class AssistantContextBuilder {

    private final AssociateRepository associates;
    private final AllocationRepository allocations;
    private final AssociateSkillRepository associateSkills;
    private final OpenPositionRepository positions;
    private final PositionSkillRepository positionSkills;

    public AssistantContextBuilder(AssociateRepository associates, AllocationRepository allocations,
                                   AssociateSkillRepository associateSkills, OpenPositionRepository positions,
                                   PositionSkillRepository positionSkills) {
        this.associates = associates;
        this.allocations = allocations;
        this.associateSkills = associateSkills;
        this.positions = positions;
        this.positionSkills = positionSkills;
    }

    @Transactional(readOnly = true)
    public String build() {
        StringBuilder sb = new StringBuilder();
        sb.append("You are the OmiVertex AI Assistant for Softility's internal resource-management ")
          .append("platform. Answer questions about the workforce data below concisely and accurately. ")
          .append("Use short bullet lists where helpful. If the data cannot answer the question, say so ")
          .append("— never invent people, projects, or numbers.\n\n");

        List<Associate> all = associates.findAll();
        List<Associate> active = all.stream().filter(a -> a.getStatus() == EntityStatus.ACTIVE).toList();
        List<Allocation> allAllocations = allocations.findAllWithDetails();
        Map<Long, List<Allocation>> byAssociate = allAllocations.stream()
                .collect(Collectors.groupingBy(a -> a.getAssociate().getId()));
        Map<Long, List<AssociateSkill>> skillsByAssociate = associateSkills.findAllWithDetails().stream()
                .collect(Collectors.groupingBy(s -> s.getAssociate().getId()));

        long benchCount = active.stream()
                .filter(a -> byAssociate.getOrDefault(a.getId(), List.of()).stream().noneMatch(Allocation::isCurrent))
                .count();
        sb.append("## Key numbers (today: ").append(LocalDate.now()).append(")\n");
        sb.append("Active associates: ").append(active.size())
          .append(" · Bench count: ").append(benchCount)
          .append(" · Open positions: ").append(positions.findAllWithDetails().stream()
                  .filter(p -> p.getStatus() == PositionStatus.OPEN).count()).append("\n\n");

        sb.append("## Associates (ACTIVE)\n");
        for (Associate a : active) {
            List<Allocation> current = byAssociate.getOrDefault(a.getId(), List.of()).stream()
                    .filter(Allocation::isCurrent).toList();
            sb.append("- ").append(a.getName()).append(" <").append(a.getEmail()).append("> · ")
              .append(a.getDesignation() == null ? "no designation" : a.getDesignation()).append(" · ")
              .append(a.getWorkMode());
            String skills = skillsByAssociate.getOrDefault(a.getId(), List.of()).stream()
                    .map(s -> s.getSkill().getName() + " (" + s.getProficiency() + ")")
                    .collect(Collectors.joining(", "));
            if (!skills.isEmpty()) {
                sb.append(" · skills: ").append(skills);
            }
            if (current.isEmpty()) {
                Long benchDays = AssociateResponse.benchDays(a, byAssociate.getOrDefault(a.getId(), List.of()));
                sb.append(" · BENCH").append(benchDays == null ? "" : " for " + benchDays + " days");
            } else {
                sb.append(" · allocated: ").append(current.stream()
                        .map(al -> al.getProject().getName() + " @" + al.getProject().getClient().getName()
                                + " (" + al.getAllocationPercent() + "%"
                                + (al.isBillable() ? ", billable" : ", non-billable")
                                + (al.getEndDate() == null ? "" : ", ends " + al.getEndDate()) + ")")
                        .collect(Collectors.joining("; ")));
            }
            if (a.getLastWorkingDay() != null && !a.getLastWorkingDay().isBefore(LocalDate.now())) {
                sb.append(" · leaving on ").append(a.getLastWorkingDay())
                  .append(" (").append(a.getExitReason()).append(")");
            }
            sb.append("\n");
        }

        List<Associate> exited = all.stream().filter(a -> a.getStatus() == EntityStatus.INACTIVE
                && a.getLastWorkingDay() != null).toList();
        if (!exited.isEmpty()) {
            sb.append("\n## Recent exits\n");
            for (Associate a : exited) {
                sb.append("- ").append(a.getName()).append(" left on ").append(a.getLastWorkingDay())
                  .append(" (").append(a.getExitReason()).append(")\n");
            }
        }

        Map<Long, List<PositionSkill>> reqsByPosition = positionSkills.findAllWithDetails().stream()
                .collect(Collectors.groupingBy(ps -> ps.getPosition().getId()));
        sb.append("\n## Open positions\n");
        positions.findAllWithDetails().stream()
                .filter(p -> p.getStatus() == PositionStatus.OPEN)
                .forEach(p -> {
                    sb.append("- ").append(p.getTitle()).append(" on ").append(p.getProject().getName())
                      .append(" @").append(p.getProject().getClient().getName())
                      .append(" (").append(p.getAllocationPercent()).append("%")
                      .append(p.isBillable() ? ", billable" : ", non-billable")
                      .append(p.getWorkMode() == null ? "" : ", " + p.getWorkMode())
                      .append(p.getStartDate() == null ? "" : ", starts " + p.getStartDate())
                      .append(")");
                    List<PositionSkill> reqs = reqsByPosition.getOrDefault(p.getId(), List.of());
                    String must = reqs.stream().filter(PositionSkill::isRequired)
                            .map(r -> r.getSkill().getName() + " (min "
                                    + (r.getMinProficiency() == null ? Proficiency.NOVICE : r.getMinProficiency()) + ")")
                            .collect(Collectors.joining(", "));
                    String nice = reqs.stream().filter(r -> !r.isRequired())
                            .map(r -> r.getSkill().getName()).collect(Collectors.joining(", "));
                    if (!must.isEmpty()) {
                        sb.append(" · must-have: ").append(must);
                    }
                    if (!nice.isEmpty()) {
                        sb.append(" · nice-to-have: ").append(nice);
                    }
                    sb.append("\n");
                });
        return sb.toString();
    }
}
