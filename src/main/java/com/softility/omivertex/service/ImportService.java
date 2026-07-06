package com.softility.omivertex.service;

import com.softility.omivertex.domain.*;
import com.softility.omivertex.repository.AssociateSkillRepository;
import com.softility.omivertex.repository.CertificationRepository;
import com.softility.omivertex.repository.SkillCategoryRepository;
import com.softility.omivertex.repository.SkillRepository;
import com.softility.omivertex.repository.AllocationRepository;
import com.softility.omivertex.repository.AssociateRepository;
import com.softility.omivertex.repository.ClientRepository;
import com.softility.omivertex.repository.ProjectRepository;
import com.softility.omivertex.web.dto.ImportSummaryResponse;
import com.softility.omivertex.web.error.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.*;

/**
 * Imports a staffing roster from .xlsx or .csv. Expected columns (header names are
 * matched case-insensitively, order does not matter):
 * ASSOCIATE NAME, COMPANY, LOCATION (city or ONSHORE/OFFSHORE), CUSTOMER, BILLABLE (B/NB), PROJECT.
 * Missing clients, projects and associates are created; existing rows are skipped.
 */
@Service
public class ImportService {

    private final ClientRepository clients;
    private final ProjectRepository projects;
    private final AssociateRepository associates;
    private final AllocationRepository allocations;
    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;
    private final SkillCategoryRepository skillCategories;
    private final SkillRepository skills;
    private final AssociateSkillRepository associateSkills;
    private final CertificationRepository certifications;
    private final SpreadsheetParser spreadsheetParser;

    public ImportService(ClientRepository clients, ProjectRepository projects,
                         AssociateRepository associates, AllocationRepository allocations,
                         AuditService auditService, PlatformTransactionManager transactionManager,
                         SkillCategoryRepository skillCategories, SkillRepository skills,
                         AssociateSkillRepository associateSkills, CertificationRepository certifications,
                         SpreadsheetParser spreadsheetParser) {
        this.clients = clients;
        this.projects = projects;
        this.associates = associates;
        this.allocations = allocations;
        this.auditService = auditService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.skillCategories = skillCategories;
        this.skills = skills;
        this.associateSkills = associateSkills;
        this.certifications = certifications;
        this.spreadsheetParser = spreadsheetParser;
    }

    /**
     * Imports the roster. With {@code dryRun} the exact same plan is executed inside
     * a transaction that is rolled back at the end, so the summary is a faithful
     * preview and the database is untouched.
     */
    public ImportSummaryResponse importRoster(MultipartFile file, boolean dryRun, boolean ignoreNovice) {
        String filename = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase();
        if (filename.endsWith(".xlsx")) {
            Map<String, List<Map<String, String>>> sheets = spreadsheetParser.parseWorkbook(file);
            boolean v2 = sheets.containsKey("employees") || sheets.containsKey("employeeskills")
                    || sheets.containsKey("certifications");
            return transactionTemplate.execute(status -> {
                ImportSummaryResponse summary = v2
                        ? processWorkbook(sheets, dryRun, ignoreNovice)
                        : processRows(sheets.values().iterator().next(), dryRun, true, 0, 0);
                if (dryRun) {
                    status.setRollbackOnly();
                }
                return summary;
            });
        }
        if (filename.endsWith(".csv")) {
            List<Map<String, String>> rows = spreadsheetParser.parseCsv(file);
            return transactionTemplate.execute(status -> {
                ImportSummaryResponse summary = processRows(rows, dryRun, true, 0, 0);
                if (dryRun) {
                    status.setRollbackOnly();
                }
                return summary;
            });
        }
        throw new BadRequestException("Unsupported file type; upload an .xlsx or .csv file");
    }

    /** Skill Cloud style workbook: Employees / EmployeeSkills / Certifications sheets. */
    private ImportSummaryResponse processWorkbook(Map<String, List<Map<String, String>>> sheets,
                                                  boolean dryRun, boolean ignoreNovice) {
        List<Map<String, String>> employeeRows = sheets.getOrDefault("employees", List.of());
        ImportSummaryResponse base = processRows(employeeRows, dryRun, false, 0, 0);
        List<String> errors = new ArrayList<>(base.errors());

        int skillsImported = 0;
        int certsImported = 0;
        int skipped = base.skipped();

        int rowNum = 1;
        for (Map<String, String> row : sheets.getOrDefault("employeeskills", List.of())) {
            rowNum++;
            try {
                String name = clean(value(row, "EMPLOYEE NAME", "ASSOCIATE NAME", "NAME"));
                if (name.isEmpty()) continue;
                Associate associate = associates.findByEmailIgnoreCase(emailFor(name)).orElse(null);
                if (associate == null) {
                    errors.add("EmployeeSkills row " + rowNum + ": unknown employee '" + name + "'");
                    continue;
                }
                String categoryName = clean(value(row, "CATEGORY", "SKILL CATEGORY"));
                String skillName = clean(value(row, "SKILL", "TOOL", "SKILL/TOOL"));
                Proficiency proficiency = parseProficiency(value(row, "PROFICIENCY", "LEVEL"));
                if (categoryName.isEmpty() || skillName.isEmpty() || proficiency == null) {
                    errors.add("EmployeeSkills row " + rowNum + ": CATEGORY, SKILL and a valid PROFICIENCY are required");
                    continue;
                }
                if (ignoreNovice && proficiency == Proficiency.NOVICE) {
                    skipped++;
                    continue;
                }
                SkillCategory category = skillCategories.findByNameIgnoreCase(categoryName).orElseGet(() -> {
                    SkillCategory c = new SkillCategory();
                    c.setName(categoryName);
                    return skillCategories.save(c);
                });
                Skill skill = skills.findByNameIgnoreCaseAndCategoryId(skillName, category.getId()).orElseGet(() -> {
                    Skill sk = new Skill();
                    sk.setName(skillName);
                    sk.setCategory(category);
                    return skills.save(sk);
                });
                AssociateSkill rated = associateSkills.findByAssociateId(associate.getId()).stream()
                        .filter(as -> as.getSkill().getId().equals(skill.getId()))
                        .findFirst().orElse(null);
                if (rated == null) {
                    rated = new AssociateSkill();
                    rated.setAssociate(associate);
                    rated.setSkill(skill);
                }
                rated.setProficiency(proficiency);
                associateSkills.save(rated);
                skillsImported++;
            } catch (Exception ex) {
                errors.add("EmployeeSkills row " + rowNum + ": " + ex.getMessage());
            }
        }

        rowNum = 1;
        for (Map<String, String> row : sheets.getOrDefault("certifications", List.of())) {
            rowNum++;
            try {
                String name = clean(value(row, "EMPLOYEE NAME", "ASSOCIATE NAME", "NAME"));
                String certName = clean(value(row, "CERTIFICATE NAME", "CERTIFICATION", "CERT NAME"));
                if (name.isEmpty() || certName.isEmpty()) continue;
                Associate associate = associates.findByEmailIgnoreCase(emailFor(name)).orElse(null);
                if (associate == null) {
                    errors.add("Certifications row " + rowNum + ": unknown employee '" + name + "'");
                    continue;
                }
                boolean exists = certifications.findByAssociateIdOrderByExpiryDateAsc(associate.getId()).stream()
                        .anyMatch(c -> c.getName().equalsIgnoreCase(certName));
                if (exists) {
                    skipped++;
                    continue;
                }
                Certification cert = new Certification();
                cert.setAssociate(associate);
                cert.setName(certName);
                cert.setAuthority(emptyToNull(clean(value(row, "AUTHORITY", "ISSUING AUTHORITY"))));
                cert.setCredentialId(emptyToNull(clean(value(row, "CREDENTIAL ID", "CREDENTIAL"))));
                cert.setIssuedDate(parseDate(value(row, "ISSUED", "ISSUED DATE"), errors, "Certifications row " + rowNum + " ISSUED"));
                cert.setExpiryDate(parseDate(value(row, "EXPIRES", "EXPIRY", "EXPIRY DATE"), errors, "Certifications row " + rowNum + " EXPIRES"));
                certifications.save(cert);
                certsImported++;
            } catch (Exception ex) {
                errors.add("Certifications row " + rowNum + ": " + ex.getMessage());
            }
        }

        if (!dryRun) {
            auditService.record("IMPORTED", "Import", null,
                    "Imported Skill Cloud workbook: " + base.rowsProcessed() + " employee rows, "
                    + base.associatesCreated() + " associates, " + skillsImported + " skills, "
                    + certsImported + " certifications, " + skipped + " skipped");
        }
        return new ImportSummaryResponse(base.rowsProcessed(), base.clientsCreated(), base.projectsCreated(),
                base.associatesCreated(), base.allocationsCreated(), skillsImported, certsImported,
                skipped, errors, dryRun);
    }

    private static Proficiency parseProficiency(String value) {
        String v = value.trim().toUpperCase().replace(" ", "_").replace("-", "_");
        if (v.isEmpty()) return null;
        if (v.equals("FUNCTIONALUSER")) return Proficiency.FUNCTIONAL_USER;
        if (v.equals("ADVANCED")) return Proficiency.ADVANCE;
        try {
            return Proficiency.valueOf(v);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static java.time.LocalDate parseDate(String value, List<String> errors, String context) {
        String v = value.trim();
        if (v.isEmpty()) return null;
        for (var fmt : List.of(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE,
                java.time.format.DateTimeFormatter.ofPattern("M/d/yyyy"),
                java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.ENGLISH))) {
            try {
                return java.time.LocalDate.parse(v, fmt);
            } catch (java.time.format.DateTimeParseException ignored) {
            }
        }
        errors.add(context + ": unparseable date '" + v + "'");
        return null;
    }

    private ImportSummaryResponse processRows(List<Map<String, String>> rows, boolean dryRun,
                                              boolean recordAudit, int skillsImported, int certificationsImported) {
        int rowsProcessed = 0, clientsCreated = 0, projectsCreated = 0,
                associatesCreated = 0, allocationsCreated = 0, skipped = 0;
        List<String> errors = new ArrayList<>();

        int rowNumber = 1;
        for (Map<String, String> row : rows) {
            rowNumber++;
            try {
                String name = clean(value(row, "ASSOCIATE NAME", "NAME", "ASSOCIATE"));
                String customer = clean(value(row, "CUSTOMER", "CLIENT"));
                String projectName = clean(value(row, "PROJECT"));
                if (name.isEmpty()) {
                    continue; // blank row
                }
                rowsProcessed++;
                if (customer.isEmpty() || projectName.isEmpty()) {
                    errors.add("Row " + rowNumber + ": CUSTOMER and PROJECT are required");
                    continue;
                }
                String company = clean(value(row, "COMPANY"));
                String locationRaw = clean(value(row, "LOCATION"));
                String shoreRaw = clean(value(row, "ONSHORE/OFFSHORE", "WORK MODE", "SHORE"));
                String billableRaw = clean(value(row, "BILLABLE", "BILLING"));

                WorkMode workMode = parseWorkMode(shoreRaw.isEmpty() ? locationRaw : shoreRaw);
                String location = isShoreValue(locationRaw) ? null : emptyToNull(locationRaw);
                boolean billable = parseBillable(billableRaw);

                Client client = clients.findByNameIgnoreCase(customer).orElse(null);
                if (client == null) {
                    client = new Client();
                    client.setName(customer);
                    client = clients.save(client);
                    clientsCreated++;
                }

                Project project = projects.findByNameIgnoreCaseAndClientId(projectName, client.getId()).orElse(null);
                if (project == null) {
                    project = new Project();
                    project.setName(projectName);
                    project.setClient(client);
                    project.setCode(uniqueProjectCode(client.getName(), projectName));
                    project.setStartDate(LocalDate.now());
                    project = projects.save(project);
                    projectsCreated++;
                }

                String email = emailFor(name);
                Associate associate = associates.findByEmailIgnoreCase(email).orElse(null);
                if (associate == null) {
                    associate = new Associate();
                    associate.setName(name);
                    associate.setEmail(email);
                    associate.setCompany(company.isEmpty() ? "Softility" : company);
                    associate.setLocation(location);
                    associate.setWorkMode(workMode);
                    associate.setPrimarySkill(emptyToNull(clean(value(row, "SKILL", "PRIMARY SKILL", "TECHNOLOGY"))));
                    associate = associates.save(associate);
                    associatesCreated++;
                }

                if (allocations.existsByAssociateIdAndProjectIdAndEndDateIsNull(associate.getId(), project.getId())) {
                    skipped++;
                } else {
                    Allocation allocation = new Allocation();
                    allocation.setAssociate(associate);
                    allocation.setProject(project);
                    allocation.setBillable(billable);
                    allocation.setAllocationPercent(100);
                    allocation.setStartDate(LocalDate.now());
                    allocations.save(allocation);
                    allocationsCreated++;
                }
            } catch (Exception ex) {
                errors.add("Row " + rowNumber + ": " + ex.getMessage());
            }
        }

        if (!dryRun && recordAudit) {
            auditService.record("IMPORTED", "Import", null,
                    "Imported roster: " + rowsProcessed + " rows, " + associatesCreated + " associates, "
                    + clientsCreated + " clients, " + projectsCreated + " projects, "
                    + allocationsCreated + " allocations created, " + skipped + " skipped");
        }
        return new ImportSummaryResponse(rowsProcessed, clientsCreated, projectsCreated,
                associatesCreated, allocationsCreated, skillsImported, certificationsImported,
                skipped, errors, dryRun);
    }

    private static String value(Map<String, String> row, String... keys) {
        for (String key : keys) {
            String v = row.get(key);
            if (v != null && !v.isBlank()) return v;
        }
        return "";
    }

    /** Trims and strips roster annotations such as a trailing asterisk. */
    private static String clean(String value) {
        return value.replaceAll("\\*+$", "").trim();
    }

    private static String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }

    private static boolean isShoreValue(String value) {
        String v = value.toUpperCase();
        return v.equals("ONSHORE") || v.equals("OFFSHORE") || v.equals("ONSITE") || v.equals("OFFSITE");
    }

    private static WorkMode parseWorkMode(String value) {
        String v = value.toUpperCase();
        if (v.startsWith("ON")) return WorkMode.ONSHORE;
        if (v.startsWith("OFF")) return WorkMode.OFFSHORE;
        return WorkMode.OFFSHORE;
    }

    private static boolean parseBillable(String value) {
        return switch (value.toUpperCase()) {
            case "B", "Y", "YES", "TRUE", "BILLABLE", "1" -> true;
            default -> false;
        };
    }

    private static String emailFor(String name) {
        return EmailNaming.forName(name);
    }

    private String uniqueProjectCode(String clientName, String projectName) {
        String prefix = clientName.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        prefix = prefix.substring(0, Math.min(3, prefix.length()));
        String base = prefix + "-" + projectName.replaceAll("[^A-Za-z0-9]+", "").toUpperCase();
        String code = base;
        int n = 2;
        while (projects.existsByCodeIgnoreCase(code)) {
            code = base + "-" + n++;
        }
        return code;
    }
}
