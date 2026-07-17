package com.softility.omivertex.service;

import com.softility.omivertex.domain.AccessStatus;
import com.softility.omivertex.domain.Allocation;
import com.softility.omivertex.domain.AppUser;
import com.softility.omivertex.domain.Associate;
import com.softility.omivertex.domain.AssociateSkill;
import com.softility.omivertex.domain.AuditEntry;
import com.softility.omivertex.domain.Certification;
import com.softility.omivertex.domain.Client;
import com.softility.omivertex.domain.EntityStatus;
import com.softility.omivertex.domain.PositionSkill;
import com.softility.omivertex.domain.PositionStatus;
import com.softility.omivertex.domain.Proficiency;
import com.softility.omivertex.domain.Project;
import com.softility.omivertex.domain.ProfileChangeRequest;
import com.softility.omivertex.domain.ProfileChangeStatus;
import com.softility.omivertex.repository.AllocationRepository;
import com.softility.omivertex.repository.AppUserRepository;
import com.softility.omivertex.repository.AssociateRepository;
import com.softility.omivertex.repository.AssociateSkillRepository;
import com.softility.omivertex.repository.AuditEntryRepository;
import com.softility.omivertex.repository.CertificationRepository;
import com.softility.omivertex.repository.ClientRepository;
import com.softility.omivertex.repository.OpenPositionRepository;
import com.softility.omivertex.repository.PositionSkillRepository;
import com.softility.omivertex.repository.ProfileChangeRequestRepository;
import com.softility.omivertex.repository.ProjectRepository;
import com.softility.omivertex.web.dto.AssociateResponse;
import com.softility.omivertex.web.dto.MatchCandidateResponse;
import com.softility.omivertex.web.dto.DashboardSummaryResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
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
    private final SkillGapService skillGapService;
    private final DashboardService dashboardService;
    private final PositionService positionService;
    private final ProfileChangeRequestRepository profileChanges;
    private final AppUserRepository appUsers;
    private final AuditEntryRepository auditEntries;

    public AssistantContextBuilder(AssociateRepository associates, AllocationRepository allocations,
                                   AssociateSkillRepository associateSkills, CertificationRepository certifications,
                                   OpenPositionRepository positions, PositionSkillRepository positionSkills,
                                   ClientRepository clients, ProjectRepository projects,
                                   SkillGapService skillGapService, DashboardService dashboardService,
                                   PositionService positionService, ProfileChangeRequestRepository profileChanges,
                                   AppUserRepository appUsers, AuditEntryRepository auditEntries) {
        this.associates = associates;
        this.allocations = allocations;
        this.associateSkills = associateSkills;
        this.certifications = certifications;
        this.positions = positions;
        this.positionSkills = positionSkills;
        this.clients = clients;
        this.projects = projects;
        this.skillGapService = skillGapService;
        this.dashboardService = dashboardService;
        this.positionService = positionService;
        this.profileChanges = profileChanges;
        this.appUsers = appUsers;
        this.auditEntries = auditEntries;
    }

    /** Standing context: instructions + aggregate counts. Never roster rows. */
    public String build() {
        return build(false);
    }

    public String build(boolean adminTools) {
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
                + "get_associate_detail, list_rolloffs, list_open_positions, get_position_matches, "
                + "list_clients, list_projects, get_skill_gaps, list_expiring_certifications, "
                + "get_workforce_summary, list_bench_aging, get_position_match_summary) "
                + (adminTools
                        ? "As an admin you can also use list_pending_approvals (what awaits "
                                + "approval) and get_audit_history (who changed what, when). "
                        : "")
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
        appendOverflow(sb, matches.size());
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

    /**
     * Read tool: every client, with how many projects each one runs. The standing
     * context only carries the client <em>count</em>, and a client with no open
     * position appears in no other tool's output — without this the assistant knows
     * how many clients exist but cannot name them.
     */
    public String listClients() {
        Map<Long, Long> projectCounts = projects.findAll().stream()
                .collect(Collectors.groupingBy(p -> p.getClient().getId(), Collectors.counting()));
        List<Client> all = clients.findAll().stream()
                .sorted(Comparator.comparing(Client::getName))
                .toList();
        if (all.isEmpty()) {
            return "No clients.";
        }

        StringBuilder sb = new StringBuilder();
        for (Client c : all.stream().limit(MAX_TOOL_ROWS).toList()) {
            sb.append("- ").append(c.getName());
            if (c.getIndustry() != null && !c.getIndustry().isBlank()) {
                sb.append(" · ").append(c.getIndustry());
            }
            if (c.getLocation() != null && !c.getLocation().isBlank()) {
                sb.append(" · ").append(c.getLocation());
            }
            long count = projectCounts.getOrDefault(c.getId(), 0L);
            sb.append(" · ").append(count == 0 ? "no projects"
                    : count == 1 ? "1 project" : count + " projects");
            if (c.getStatus() != EntityStatus.ACTIVE) {
                sb.append(" · ").append(c.getStatus());
            }
            sb.append("\n");
        }
        appendOverflow(sb, all.size());
        return sb.toString();
    }

    /**
     * Read tool: every project, optionally only one client's. Like {@link #listClients()}
     * this exists so the assistant can discover names it has never been told; {@code
     * projectDetail} can only drill into a project whose name it already knows.
     */
    public String listProjects(String clientName) {
        List<Project> matches = projects.findAllByOrderByNameAsc().stream()
                .filter(p -> clientName == null || clientName.isBlank()
                        || p.getClient().getName().toLowerCase()
                                .contains(clientName.trim().toLowerCase()))
                .toList();
        if (matches.isEmpty()) {
            return "No matching projects.";
        }

        StringBuilder sb = new StringBuilder();
        for (Project p : matches.stream().limit(MAX_TOOL_ROWS).toList()) {
            sb.append("- ").append(p.getName()).append(" · ").append(p.getCode())
              .append(" @").append(p.getClient().getName())
              .append(" · ").append(p.getStatus())
              .append(" · ").append(p.getStartDate() == null ? "?" : p.getStartDate())
              .append("–").append(p.getEndDate() == null ? "open" : p.getEndDate())
              .append("\n");
        }
        appendOverflow(sb, matches.size());
        return sb.toString();
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

    /** Read tool: skill supply vs demand, worst gap first (SkillGapService owns the math). */
    public String skillGaps() {
        List<DashboardSummaryResponse.SkillGap> gaps = skillGapService.dashboardPanel();
        if (gaps.isEmpty()) {
            return "No open positions demand any skills right now.";
        }
        return gaps.stream()
                .map(g -> "- " + g.skillName() + " (" + g.category() + ") · demand " + g.demand()
                        + " · bench supply " + g.benchSupply() + " · total holders " + g.totalSupply()
                        + " · gap " + g.gap())
                .collect(Collectors.joining("\n"));
    }

    /** Read tool: certifications expiring in the window, soonest first, capped. */
    public String expiringCertifications(int withinDays) {
        List<DashboardSummaryResponse.ExpiringCert> certs = dashboardService.expiringCerts(withinDays);
        if (certs.isEmpty()) {
            return "No certifications expire within " + withinDays + " days.";
        }
        StringBuilder sb = new StringBuilder();
        for (DashboardSummaryResponse.ExpiringCert c : certs.stream().limit(MAX_TOOL_ROWS).toList()) {
            sb.append("- ").append(c.associateName()).append(" — ").append(c.name())
              .append(", expires ").append(c.expiryDate())
              .append(" (in ").append(c.daysLeft()).append(" days)\n");
        }
        appendOverflow(sb, certs.size());
        return sb.toString();
    }

    /** Read tool: org-health KPIs, bench aging, six-month trend, and utilization forecast. */
    public String workforceSummary() {
        DashboardSummaryResponse s = dashboardService.summary();
        StringBuilder sb = new StringBuilder();
        sb.append("Active associates: ").append(s.totalAssociates())
          .append(" · billable ").append(s.billableCount())
          .append(" · non-billable ").append(s.nonBillableCount())
          .append(" · bench ").append(s.benchCount())
          .append(" · onshore ").append(s.onshoreCount())
          .append(" · offshore ").append(s.offshoreCount()).append("\n");
        sb.append("Clients: ").append(s.totalClients())
          .append(" · active projects: ").append(s.activeProjects())
          .append(" · open positions: ").append(s.openPositions())
          .append(" · utilization: ").append(s.utilizationPercent()).append("%")
          .append(" · exits last 12 months: ").append(s.exitsLast12Months()).append("\n");
        sb.append("Bench aging: ").append(benchBuckets(s.benchAging())).append("\n");
        sb.append("Staffing trend (allocated/billable per month): ").append(s.staffingTrend().stream()
                .map(t -> t.month() + " " + t.total() + "/" + t.billable())
                .collect(Collectors.joining(", "))).append("\n");
        sb.append("Utilization forecast:\n");
        for (DashboardSummaryResponse.ForecastPoint p : s.utilizationForecast()) {
            sb.append("- ").append(p.label()).append(": ").append(p.percent()).append("%");
            if (p.deltaPoints() != 0) {
                sb.append(" (").append(p.deltaPoints() > 0 ? "+" : "")
                  .append(p.deltaPoints()).append(" vs today)");
            }
            if (!p.drivers().isEmpty()) {
                sb.append(" — ").append(p.drivers().stream()
                        .map(d -> d.kind() + " " + d.associateName()
                                + (d.projectName() == null ? "" : " (" + d.projectName() + ")")
                                + " " + d.date())
                        .collect(Collectors.joining("; ")));
                if (p.omittedDrivers() > 0) {
                    sb.append(" …and ").append(p.omittedDrivers()).append(" more");
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /** Read tool: bench bucket counts + the bench roster, longest-benched first, capped. */
    public String benchAging() {
        DashboardSummaryResponse s = dashboardService.summary();
        List<DashboardSummaryResponse.BenchAssociate> bench = s.benchAssociates();
        if (bench.isEmpty()) {
            return "No one is on the bench.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Bench: ").append(bench.size()).append(" people — ")
          .append(benchBuckets(s.benchAging())).append("\n");
        for (DashboardSummaryResponse.BenchAssociate b : bench.stream().limit(MAX_TOOL_ROWS).toList()) {
            sb.append("- ").append(b.name()).append(" · ")
              .append(b.designation() == null ? "no designation" : b.designation())
              .append(" · ").append(b.benchDays()).append(" days on bench\n");
        }
        appendOverflow(sb, bench.size());
        return sb.toString();
    }

    /**
     * Read tool: bench-match overview for EVERY open position in one call.
     * "Which open positions have no bench match?" needs one lookup per position
     * through {@code get_position_matches} — more than the per-turn tool budget
     * once a few seats are open. This tool answers it in a single round.
     */
    public String positionMatchSummary() {
        List<com.softility.omivertex.domain.OpenPosition> open = positions.findAllWithDetails().stream()
                .filter(p -> p.getStatus() == PositionStatus.OPEN)
                .sorted(Comparator.comparing(com.softility.omivertex.domain.OpenPosition::getTitle))
                .toList();
        if (open.isEmpty()) {
            return "No open positions.";
        }
        StringBuilder sb = new StringBuilder();
        for (var p : open.stream().limit(MAX_TOOL_ROWS).toList()) {
            List<MatchCandidateResponse> candidates = positionService.matches(p.getId());
            List<MatchCandidateResponse> full = candidates.stream()
                    .filter(MatchCandidateResponse::fullMatch).toList();
            sb.append("- ").append(p.getTitle())
              .append(" on ").append(p.getProject().getName())
              .append(" @").append(p.getProject().getClient().getName())
              .append(": ");
            if (!full.isEmpty()) {
                sb.append(full.size()).append(full.size() == 1 ? " full bench match — " : " full bench matches — ")
                  .append(full.stream().limit(3)
                          .map(c -> c.name() + (c.benchDays() == null ? "" : " (" + c.benchDays() + "d bench)"))
                          .collect(Collectors.joining(", ")));
            } else if (!candidates.isEmpty()) {
                MatchCandidateResponse best = candidates.get(0);
                sb.append("NO full match — closest: ").append(best.name())
                  .append(", missing ").append(String.join(", ", best.missingRequirements()));
            } else {
                sb.append("NO full match — no available candidates at all");
            }
            sb.append("\n");
        }
        appendOverflow(sb, open.size());
        return sb.toString();
    }

    /** ADMIN-only read tool: everything waiting on an admin — profile changes + access requests. */
    public String pendingApprovals() {
        List<ProfileChangeRequest> changes = profileChanges.findAllByStatus(ProfileChangeStatus.PENDING);
        List<AppUser> access = appUsers.findAllByStatusOrderByCreatedAtAsc(AccessStatus.PENDING);
        if (changes.isEmpty() && access.isEmpty()) {
            return "Nothing is waiting for approval.";
        }
        StringBuilder sb = new StringBuilder();
        if (!changes.isEmpty()) {
            sb.append("Profile changes pending:\n");
            for (ProfileChangeRequest c : changes.stream().limit(MAX_TOOL_ROWS).toList()) {
                sb.append("- ").append(c.getAssociate().getName())
                  .append(" · ").append(c.getType())
                  .append(c.getCreatedAt() == null ? "" : " · requested "
                          + c.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate())
                  .append("\n");
            }
            appendOverflow(sb, changes.size());
        }
        if (!access.isEmpty()) {
            sb.append("Access requests pending:\n");
            for (AppUser u : access.stream().limit(MAX_TOOL_ROWS).toList()) {
                sb.append("- ").append(u.getName() == null || u.getName().isBlank()
                        ? u.getEmail() : u.getName() + " (" + u.getEmail() + ")").append("\n");
            }
            appendOverflow(sb, access.size());
        }
        return sb.toString();
    }

    /** ADMIN-only read tool: recent audit entries, newest first, optionally one entity type. */
    public String auditHistory(String entityType, int limit) {
        String type = entityType == null ? null : entityType.trim();
        int cap = Math.min(Math.max(limit, 1), MAX_TOOL_ROWS);
        PageRequest page = PageRequest.of(0, cap);
        List<AuditEntry> entries = type == null || type.isBlank()
                ? auditEntries.findAllByOrderByIdDesc(page)
                : auditEntries.findByEntityTypeOrderByIdDesc(type, page);
        if (entries.isEmpty()) {
            return type == null || type.isBlank() ? "No audit entries."
                    : "No audit entries for type \"" + type + "\".";
        }
        return entries.stream()
                .map(e -> "- " + (e.getTimestamp() == null ? ""
                        : e.getTimestamp().truncatedTo(ChronoUnit.MINUTES) + " · ")
                        + e.getUsername() + " · " + e.getAction() + " " + e.getEntityType()
                        + (e.getEntityId() == null ? "" : "#" + e.getEntityId())
                        + " · " + e.getSummary())
                .collect(Collectors.joining("\n"));
    }

    // ---- shared row fragments ----

    /** The one place the bench-aging bucket fragment is worded, for every tool that shows it. */
    private static String benchBuckets(DashboardSummaryResponse.BenchAging aging) {
        return aging.days0to30() + " ≤30d · " + aging.days31to60() + " 31–60d · "
                + aging.days60plus() + " >60d";
    }

    /** The one place the row-cap overflow line is worded, for every capped tool. */
    private void appendOverflow(StringBuilder sb, int totalMatches) {
        if (totalMatches > MAX_TOOL_ROWS) {
            sb.append("…and ").append(totalMatches - MAX_TOOL_ROWS)
              .append(" more — refine the search.\n");
        }
    }

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
