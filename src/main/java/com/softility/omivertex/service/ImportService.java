package com.softility.omivertex.service;

import com.softility.omivertex.domain.*;
import com.softility.omivertex.repository.AllocationRepository;
import com.softility.omivertex.repository.AssociateRepository;
import com.softility.omivertex.repository.ClientRepository;
import com.softility.omivertex.repository.ProjectRepository;
import com.softility.omivertex.web.dto.ImportSummaryResponse;
import com.softility.omivertex.web.error.BadRequestException;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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

    public ImportService(ClientRepository clients, ProjectRepository projects,
                         AssociateRepository associates, AllocationRepository allocations,
                         AuditService auditService, PlatformTransactionManager transactionManager) {
        this.clients = clients;
        this.projects = projects;
        this.associates = associates;
        this.allocations = allocations;
        this.auditService = auditService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Imports the roster. With {@code dryRun} the exact same plan is executed inside
     * a transaction that is rolled back at the end, so the summary is a faithful
     * preview and the database is untouched.
     */
    public ImportSummaryResponse importRoster(MultipartFile file, boolean dryRun) {
        String filename = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase();
        List<Map<String, String>> rows;
        if (filename.endsWith(".xlsx")) {
            rows = parseXlsx(file);
        } else if (filename.endsWith(".csv")) {
            rows = parseCsv(file);
        } else {
            throw new BadRequestException("Unsupported file type; upload an .xlsx or .csv file");
        }

        return transactionTemplate.execute(status -> {
            ImportSummaryResponse summary = processRows(rows, dryRun);
            if (dryRun) {
                status.setRollbackOnly();
            }
            return summary;
        });
    }

    private ImportSummaryResponse processRows(List<Map<String, String>> rows, boolean dryRun) {
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

        if (!dryRun) {
            auditService.record("IMPORTED", "Import", null,
                    "Imported roster: " + rowsProcessed + " rows, " + associatesCreated + " associates, "
                    + clientsCreated + " clients, " + projectsCreated + " projects, "
                    + allocationsCreated + " allocations created, " + skipped + " skipped");
        }
        return new ImportSummaryResponse(rowsProcessed, clientsCreated, projectsCreated,
                associatesCreated, allocationsCreated, skipped, errors, dryRun);
    }

    private List<Map<String, String>> parseXlsx(MultipartFile file) {
        try (XSSFWorkbook wb = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            Iterator<Row> it = sheet.iterator();
            if (!it.hasNext()) return List.of();
            List<String> headers = new ArrayList<>();
            for (var cell : it.next()) {
                headers.add(fmt.formatCellValue(cell).trim().toUpperCase());
            }
            List<Map<String, String>> rows = new ArrayList<>();
            while (it.hasNext()) {
                Row row = it.next();
                Map<String, String> values = new HashMap<>();
                for (int c = 0; c < headers.size(); c++) {
                    values.put(headers.get(c), fmt.formatCellValue(row.getCell(c)).trim());
                }
                rows.add(values);
            }
            return rows;
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Could not read Excel file: " + e.getMessage());
        }
    }

    private List<Map<String, String>> parseCsv(MultipartFile file) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) return List.of();
            String[] headers = splitCsv(headerLine);
            for (int i = 0; i < headers.length; i++) headers[i] = headers[i].trim().toUpperCase();
            List<Map<String, String>> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] cells = splitCsv(line);
                Map<String, String> values = new HashMap<>();
                for (int c = 0; c < headers.length && c < cells.length; c++) {
                    values.put(headers[c], cells[c].trim());
                }
                rows.add(values);
            }
            return rows;
        } catch (Exception e) {
            throw new BadRequestException("Could not read CSV file: " + e.getMessage());
        }
    }

    /** Splits a CSV line honoring double-quoted fields. */
    private String[] splitCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                quoted = !quoted;
            } else if (ch == ',' && !quoted) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        out.add(cur.toString());
        return out.toArray(String[]::new);
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
        String slug = name.toLowerCase().replaceAll("[^a-z0-9]+", ".").replaceAll("^\\.|\\.$", "");
        return slug + "@softility.com";
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
