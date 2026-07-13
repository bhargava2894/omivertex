package com.softility.omivertex.service;

import com.softility.omivertex.domain.Allocation;
import com.softility.omivertex.domain.Associate;
import com.softility.omivertex.domain.AssociateSkill;
import com.softility.omivertex.domain.Certification;
import com.softility.omivertex.domain.EntityStatus;
import com.softility.omivertex.domain.PositionSkill;
import com.softility.omivertex.domain.PositionStatus;
import com.softility.omivertex.domain.Proficiency;
import com.softility.omivertex.domain.Project;
import com.softility.omivertex.repository.AllocationRepository;
import com.softility.omivertex.repository.AssociateRepository;
import com.softility.omivertex.repository.AssociateSkillRepository;
import com.softility.omivertex.repository.CertificationRepository;
import com.softility.omivertex.repository.ClientRepository;
import com.softility.omivertex.repository.OpenPositionRepository;
import com.softility.omivertex.repository.PositionSkillRepository;
import com.softility.omivertex.repository.ProjectRepository;
import com.softility.omivertex.web.dto.AssociateResponse;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Formats workforce data for the AI assistant. The standing context ({@link
 * #build()}) carries ONLY aggregate counts — no names, emails, or roster rows
 * (privacy/scale decision 2026-07-11, superseding the 2026-07-10 full-detail
 * decision). Specific slices leave the server only when the model requests
 * them through a read tool, and every tool result is row-capped.
 */
@Component
@Transactional(readOnly = true)
public class AssistantContextBuilder {

    /** Max rows any tool result returns — a tool reply can never blow up the prompt. */
    static final int MAX_TOOL_ROWS = 25;

    private final AssociateRepository associates;
    private final AllocationRepository allocations;
    private final AssociateSkillRepository associateSkills;
    private final CertificationRepository certifications;
    private final OpenPositionRepository positions;
    private final PositionSkillRepository positionSkills;
    private final ClientRepository clients;
    private final ProjectRepository projects;

    public AssistantContextBuilder(AssociateRepository associates, AllocationRepository allocations,
                                   AssociateSkillRepository associateSkills, CertificationRepository certifications,
                                   OpenPositionRepository positions, PositionSkillRepository positionSkills,
                                   ClientRepository clients, ProjectRepository projects) {
        this.associates = associates;
        this.allocations = allocations;
        this.associateSkills = associateSkills;
        this.certifications = certifications;
        this.positions = positions;
        this.positionSkills = positionSkills;
        this.clients = clients;
        this.projects = projects;
    }

    /** Standing context: instructions + aggregate counts. Never roster rows. */
    public String build() {
        List<Associate> active = activeAssociates();
        Map<Long, List<Allocation>> byAssociate = allocationsByAssociate();
        long benchCount = active.stream()
                .filter(a -> byAssociate.getOrDefault(a.getId(), List.of()).stream()
                        .noneMatch(Allocation::isCurrent))
                .count();
        long openPositions = positions.findAllWithDetails().stream()
                .filter(p -> p.getStatus() == PositionStatus.OPEN).count();

        return "You are Mirai, the AI assistant for Softility's internal resource-management "
                + "platform. Answer questions about the workforce concisely and accurately, using "
                + "short bullet lists where helpful. Use your lookup tools (search_associates, "
                + "get_associate_detail, list_rolloffs, list_open_positions, get_position_matches) "
                + "to fetch specifics before answering. If the tools cannot answer the question, "
                + "say so — never invent people, projects, or numbers.\n\n"
                + "## Key numbers (today: " + LocalDate.now() + ")\n"
                + "Active associates: " + active.size()
                + " · Bench count: " + benchCount
                + " · Open positions: " + openPositions
                + " · Clients: " + clients.count()
                + " · Projects: " + projects.count() + "\n";
    }

    /** Read tool: filtered roster slice, capped at {@link #MAX_TOOL_ROWS}. */
    public String searchAssociates(String name, String skillName, Proficiency minProficiency,
                                   boolean benchOnly) {
        Map<Long, List<Allocation>> byAssociate = allocationsByAssociate();
        Map<Long, List<AssociateSkill>> skillsByAssociate = skillsByAssociate();
        Proficiency threshold = minProficiency == null ? Proficiency.NOVICE : minProficiency;

        List<Associate> matches = activeAssociates().stream()
                .filter(a -> name == null || name.isBlank()
                        || a.getName().toLowerCase().contains(name.trim().toLowerCase()))
                .filter(a -> skillName == null || skillName.isBlank()
                        || skillsByAssociate.getOrDefault(a.getId(), List.of()).stream()
                                .anyMatch(s -> s.getSkill().getName().toLowerCase()
                                        .contains(skillName.trim().toLowerCase())
                                        && s.getProficiency().ordinal() >= threshold.ordinal()))
                .filter(a -> !benchOnly || byAssociate.getOrDefault(a.getId(), List.of()).stream()
                        .noneMatch(Allocation::isCurrent))
                .sorted(Comparator.comparing(Associate::getName))
                .toList();
        if (matches.isEmpty()) {
            return "No matching associates.";
        }

        StringBuilder sb = new StringBuilder();
        for (Associate a : matches.stream().limit(MAX_TOOL_ROWS).toList()) {
            sb.append("- ").append(a.getName()).append(" · ")
              .append(a.getDesignation() == null ? "no designation" : a.getDesignation())
              .append(" · ").append(a.getWorkMode());
            appendStaffing(sb, a, byAssociate.getOrDefault(a.getId(), List.of()));
            if (skillName != null && !skillName.isBlank()) {
                String matched = skillsByAssociate.getOrDefault(a.getId(), List.of()).stream()
                        .filter(s -> s.getSkill().getName().toLowerCase()
                                .contains(skillName.trim().toLowerCase()))
                        .map(s -> s.getSkill().getName() + " (" + s.getProficiency() + ")")
                        .collect(Collectors.joining(", "));
                if (!matched.isEmpty()) {
                    sb.append(" · matched: ").append(matched);
                }
            }
            appendUpcomingExit(sb, a);
            sb.append("\n");
        }
        if (matches.size() > MAX_TOOL_ROWS) {
            sb.append("…and ").append(matches.size() - MAX_TOOL_ROWS)
              .append(" more — refine the search.\n");
        }
        return sb.toString();
    }

    /** Read tool: one associate's full picture (skills, allocations, bench, exit). */
    public String associateDetail(Associate a) {
        List<Allocation> all = allocations.findByAssociateId(a.getId());
        StringBuilder sb = new StringBuilder();
        sb.append(a.getName()).append(" · ")
          .append(a.getDesignation() == null ? "no designation" : a.getDesignation())
          .append(" · ").append(a.getWorkMode());
        if (a.getStatus() == EntityStatus.INACTIVE) {
            sb.append(" · FORMER EMPLOYEE");
            if (a.getLastWorkingDay() != null) {
                sb.append(" (left ").append(a.getLastWorkingDay())
                  .append(a.getExitReason() == null ? "" : ", " + a.getExitReason()).append(")");
            }
        }
        String skills = associateSkills.findByAssociateId(a.getId()).stream()
                .map(s -> s.getSkill().getName() + " (" + s.getProficiency() + ")")
                .collect(Collectors.joining(", "));
        if (!skills.isEmpty()) {
            sb.append(" · skills: ").append(skills);
        }
        appendStaffing(sb, a, all);
        appendPastProjects(sb, all);
        appendCertifications(sb, a);
        appendUpcomingExit(sb, a);
        return sb.toString();
    }

    /** Read tool: current allocations ending within the window, soonest first. */
    public String rolloffs(int withinDays) {
        LocalDate today = LocalDate.now();
        LocalDate limit = today.plusDays(withinDays);
        List<Allocation> ending = allocations.findAllWithDetails().stream()
                .filter(Allocation::isCurrent)
                .filter(al -> al.getAssociate().getStatus() == EntityStatus.ACTIVE)
                .filter(al -> al.getEndDate() != null && !al.getEndDate().isAfter(limit))
                .sorted(Comparator.comparing(Allocation::getEndDate))
                .limit(MAX_TOOL_ROWS)
                .toList();
        if (ending.isEmpty()) {
            return "No allocations ending within " + withinDays + " days.";
        }
        return ending.stream()
                .map(al -> "- " + al.getAssociate().getName() + " — " + al.getProject().getName()
                        + " @" + al.getProject().getClient().getName()
                        + ", ends " + al.getEndDate()
                        + " (in " + ChronoUnit.DAYS.between(today, al.getEndDate()) + " days)")
                .collect(Collectors.joining("\n"));
    }

    /** Read tool: open positions with their skill requirements. */
    public String openPositions() {
        Map<Long, List<PositionSkill>> reqsByPosition = positionSkills.findAllWithDetails().stream()
                .collect(Collectors.groupingBy(ps -> ps.getPosition().getId()));
        List<String> rows = positions.findAllWithDetails().stream()
                .filter(p -> p.getStatus() == PositionStatus.OPEN)
                .limit(MAX_TOOL_ROWS)
                .map(p -> {
                    StringBuilder sb = new StringBuilder();
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
                    return sb.toString();
                })
                .toList();
        return rows.isEmpty() ? "No open positions." : String.join("\n", rows);
    }

    /** Read tool: one project's picture — who is currently staffed on it, and open seats. */
    public String projectDetail(Project p) {
        StringBuilder sb = new StringBuilder();
        sb.append(p.getName()).append(" · ").append(p.getCode())
          .append(" @").append(p.getClient().getName())
          .append(" · ").append(p.getStatus())
          .append(" · ").append(p.getStartDate() == null ? "?" : p.getStartDate())
          .append("–").append(p.getEndDate() == null ? "open" : p.getEndDate());

        List<Allocation> roster = allocations.findAllWithDetails().stream()
                .filter(al -> al.getProject().getId().equals(p.getId()))
                .filter(Allocation::isCurrent)
                .sorted(Comparator.comparing(al -> al.getAssociate().getName()))
                .limit(MAX_TOOL_ROWS)
                .toList();
        if (roster.isEmpty()) {
            sb.append(" · No one is currently allocated.");
        } else {
            sb.append(" · roster: ").append(roster.stream()
                    .map(al -> al.getAssociate().getName() + " (" + al.getAllocationPercent() + "%"
                            + (al.isBillable() ? ", billable" : ", non-billable") + ")")
                    .collect(Collectors.joining("; ")));
        }

        List<String> openSeats = positions.findAllWithDetails().stream()
                .filter(op -> op.getProject().getId().equals(p.getId()))
                .filter(op -> op.getStatus() == PositionStatus.OPEN)
                .limit(MAX_TOOL_ROWS)
                .map(op -> op.getTitle() + " (" + op.getAllocationPercent() + "%)")
                .toList();
        if (!openSeats.isEmpty()) {
            sb.append(" · open positions: ").append(String.join("; ", openSeats));
        }
        return sb.toString();
    }

    // ---- shared row fragments ----

    private void appendStaffing(StringBuilder sb, Associate a, List<Allocation> allOfTheirs) {
        List<Allocation> current = allOfTheirs.stream().filter(Allocation::isCurrent).toList();
        if (current.isEmpty()) {
            Long benchDays = AssociateResponse.benchDays(a, allOfTheirs);
            sb.append(" · BENCH").append(benchDays == null ? "" : " for " + benchDays + " days");
        } else {
            sb.append(" · allocated: ").append(current.stream()
                    .map(al -> al.getProject().getName() + " @" + al.getProject().getClient().getName()
                            + " (" + al.getAllocationPercent() + "%"
                            + (al.isBillable() ? ", billable" : ", non-billable")
                            + (al.getEndDate() == null ? "" : ", ends " + al.getEndDate()) + ")")
                    .collect(Collectors.joining("; ")));
        }
    }

    /**
     * Detail-view only (not the compact search rows): an associate's ended
     * allocations are their project history, most-recently-ended first. Without
     * this the assistant only ever sees current staffing and wrongly reports that
     * a person has no previous projects.
     */
    private void appendPastProjects(StringBuilder sb, List<Allocation> allOfTheirs) {
        LocalDate today = LocalDate.now();
        List<Allocation> past = allOfTheirs.stream()
                .filter(al -> al.getEndDate() != null && al.getEndDate().isBefore(today))
                .sorted(Comparator.comparing(Allocation::getEndDate).reversed())
                .limit(MAX_TOOL_ROWS)
                .toList();
        if (!past.isEmpty()) {
            sb.append(" · past projects: ").append(past.stream()
                    .map(al -> al.getProject().getName() + " @" + al.getProject().getClient().getName()
                            + " (" + al.getStartDate() + "–" + al.getEndDate()
                            + (al.isBillable() ? ", billable" : ", non-billable") + ")")
                    .collect(Collectors.joining("; ")));
        }
    }

    /**
     * Detail-view only: certifications the associate holds, soonest-expiring first,
     * each flagged valid or expired. Without this the assistant cannot answer
     * "is X certified in …" and wrongly implies they hold none.
     */
    private void appendCertifications(StringBuilder sb, Associate a) {
        LocalDate today = LocalDate.now();
        List<Certification> certs = certifications.findByAssociateIdOrderByExpiryDateAsc(a.getId());
        if (!certs.isEmpty()) {
            sb.append(" · certifications: ").append(certs.stream()
                    .limit(MAX_TOOL_ROWS)
                    .map(c -> c.getName()
                            + (c.getAuthority() == null || c.getAuthority().isBlank()
                                    ? "" : " (" + c.getAuthority() + ")")
                            + (c.getExpiryDate() == null ? ""
                                    : c.getExpiryDate().isBefore(today) ? ", expired " + c.getExpiryDate()
                                    : ", valid to " + c.getExpiryDate()))
                    .collect(Collectors.joining("; ")));
        }
    }

    private void appendUpcomingExit(StringBuilder sb, Associate a) {
        if (a.getLastWorkingDay() != null && !a.getLastWorkingDay().isBefore(LocalDate.now())) {
            sb.append(" · leaving on ").append(a.getLastWorkingDay())
              .append(" (").append(a.getExitReason()).append(")");
        }
    }

    private List<Associate> activeAssociates() {
        return associates.findAll().stream()
                .filter(a -> a.getStatus() == EntityStatus.ACTIVE).toList();
    }

    private Map<Long, List<Allocation>> allocationsByAssociate() {
        return allocations.findAllWithDetails().stream()
                .collect(Collectors.groupingBy(a -> a.getAssociate().getId()));
    }

    private Map<Long, List<AssociateSkill>> skillsByAssociate() {
        return associateSkills.findAllWithDetails().stream()
                .collect(Collectors.groupingBy(s -> s.getAssociate().getId()));
    }
}
