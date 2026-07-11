package com.softility.omivertex.api;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DataTransferApiTest extends ApiTestBase {

    private static final String[][] SHEET = {
            {"ASSOCIATE NAME", "COMPANY", "LOCATION", "CUSTOMER", "BILLABLE", "Project"},
            {"Nagesh Rayapudi", "Softility", "OFFSHORE", "ADMIN", "NB", "ACCOUNTS"},
            {"Vivek Vybonia*", "Softility", "OFFSHORE", "COX", "B", "ARINA"},
            {"Prashamsh T", "Softility", "ONSHORE", "COX", "B", "ARINA"},
    };

    private byte[] workbookBytes() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = wb.createSheet("Roster");
            for (int r = 0; r < SHEET.length; r++) {
                var row = sheet.createRow(r);
                for (int c = 0; c < SHEET[r].length; c++) {
                    row.createCell(c).setCellValue(SHEET[r][c]);
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void importXlsx_createsClientsProjectsAssociatesAndAllocations() throws Exception {
        var file = new MockMultipartFile("file", "roster.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbookBytes());

        mockMvc.perform(multipart("/api/v1/data/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowsProcessed").value(3))
                .andExpect(jsonPath("$.clientsCreated").value(2))
                .andExpect(jsonPath("$.projectsCreated").value(2))
                .andExpect(jsonPath("$.associatesCreated").value(3))
                .andExpect(jsonPath("$.allocationsCreated").value(3))
                .andExpect(jsonPath("$.errors", hasSize(0)));

        // asterisk stripped, billable derived from allocation, customer/project resolved
        mockMvc.perform(get("/api/v1/associates"))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[?(@.name=='Vivek Vybonia')].billable").value(true))
                .andExpect(jsonPath("$[?(@.name=='Vivek Vybonia')].currentClient").value("COX"))
                .andExpect(jsonPath("$[?(@.name=='Vivek Vybonia')].currentProject").value("ARINA"))
                .andExpect(jsonPath("$[?(@.name=='Nagesh Rayapudi')].billable").value(false))
                .andExpect(jsonPath("$[?(@.name=='Prashamsh T')].workMode").value("ONSHORE"));
    }

    @Test
    void importTwice_isIdempotent() throws Exception {
        var file = new MockMultipartFile("file", "roster.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbookBytes());
        mockMvc.perform(multipart("/api/v1/data/import").file(file)).andExpect(status().isOk());

        mockMvc.perform(multipart("/api/v1/data/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientsCreated").value(0))
                .andExpect(jsonPath("$.projectsCreated").value(0))
                .andExpect(jsonPath("$.associatesCreated").value(0))
                .andExpect(jsonPath("$.allocationsCreated").value(0))
                .andExpect(jsonPath("$.skipped").value(3));
    }

    @Test
    void importCsv_isSupported_andMapsSkillColumn() throws Exception {
        String csv = """
                ASSOCIATE NAME,COMPANY,LOCATION,CUSTOMER,BILLABLE,Project,SKILL
                Madhu Chittepu,Softility,OFFSHORE,COX,B,BIGDATA DEVOPS,Hadoop
                """;
        var file = new MockMultipartFile("file", "roster.csv", "text/csv", csv.getBytes());

        mockMvc.perform(multipart("/api/v1/data/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowsProcessed").value(1))
                .andExpect(jsonPath("$.associatesCreated").value(1));

        mockMvc.perform(get("/api/v1/associates"))
                .andExpect(jsonPath("$[0].primarySkill").value("Hadoop"));
    }

    @Test
    void importCsv_mapsJoinedDate_soBenchClockSurvivesImport() throws Exception {
        // historical roster: the join date anchors the bench clock, not the import day
        String csv = """
                ASSOCIATE NAME,COMPANY,LOCATION,JOINED DATE
                Meena Pillai,Softility,ONSHORE,%s
                """.formatted(java.time.LocalDate.now().minusDays(45));
        var file = new MockMultipartFile("file", "roster.csv", "text/csv", csv.getBytes());

        mockMvc.perform(multipart("/api/v1/data/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.associatesCreated").value(1))
                .andExpect(jsonPath("$.errors", hasSize(0)));

        mockMvc.perform(get("/api/v1/associates"))
                .andExpect(jsonPath("$[0].joinedDate").value(java.time.LocalDate.now().minusDays(45).toString()))
                .andExpect(jsonPath("$[0].benchDays").value(45));
    }

    @Test
    void importCsv_associateWithoutProject_createsBenchAssociate() throws Exception {
        // New joiners not yet staffed: only a name (no CUSTOMER/PROJECT).
        String csv = """
                ASSOCIATE NAME,COMPANY,LOCATION,ONSHORE/OFFSHORE
                Ravi Kumar,Softility,Hyderabad,OFFSHORE
                """;
        var file = new MockMultipartFile("file", "roster.csv", "text/csv", csv.getBytes());

        mockMvc.perform(multipart("/api/v1/data/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowsProcessed").value(1))
                .andExpect(jsonPath("$.associatesCreated").value(1))
                .andExpect(jsonPath("$.allocationsCreated").value(0))
                .andExpect(jsonPath("$.errors", hasSize(0)));

        mockMvc.perform(get("/api/v1/associates"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Ravi Kumar"));
    }

    @Test
    void importDryRun_previewsWithoutPersisting() throws Exception {
        var file = new MockMultipartFile("file", "roster.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbookBytes());

        mockMvc.perform(multipart("/api/v1/data/import").file(file).param("dryRun", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(true))
                .andExpect(jsonPath("$.rowsProcessed").value(3))
                .andExpect(jsonPath("$.associatesCreated").value(3))
                .andExpect(jsonPath("$.clientsCreated").value(2));

        // nothing persisted
        mockMvc.perform(get("/api/v1/associates"))
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/v1/clients"))
                .andExpect(jsonPath("$", hasSize(0)));

        // a real import afterwards persists the same plan
        mockMvc.perform(multipart("/api/v1/data/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(false))
                .andExpect(jsonPath("$.associatesCreated").value(3));
        mockMvc.perform(get("/api/v1/associates"))
                .andExpect(jsonPath("$", hasSize(3)));
    }

    private byte[] v2Workbook() throws Exception {
        String[][] employees = {
                {"ASSOCIATE NAME", "COMPANY", "LOCATION", "CUSTOMER", "BILLABLE", "Project"},
                {"Kiran Rao", "Softility", "OFFSHORE", "COX", "B", "ARINA"},
        };
        String[][] skills = {
                {"EMPLOYEE NAME", "CATEGORY", "SKILL", "PROFICIENCY"},
                {"Kiran Rao", "CI/CD", "Jenkins", "Intermediate"},
                {"Kiran Rao", "CI/CD", "GitHub", "Novice"},
        };
        String[][] certs = {
                {"EMPLOYEE NAME", "CERTIFICATE NAME", "AUTHORITY", "CREDENTIAL ID", "ISSUED", "EXPIRES"},
                {"Kiran Rao", "Certified Kubernetes Administrator", "CNCF", "CKA-987", "2025-01-01", "2027-01-01"},
        };
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (var entry : java.util.Map.of("Employees", employees, "EmployeeSkills", skills, "Certifications", certs).entrySet()) {
                var sheet = wb.createSheet(entry.getKey());
                String[][] data = entry.getValue();
                for (int r = 0; r < data.length; r++) {
                    var row = sheet.createRow(r);
                    for (int c = 0; c < data[r].length; c++) row.createCell(c).setCellValue(data[r][c]);
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void importV2_multiSheet_createsSkillsAndCertifications() throws Exception {
        var file = new MockMultipartFile("file", "skillcloud.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", v2Workbook());

        mockMvc.perform(multipart("/api/v1/data/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.associatesCreated").value(1))
                .andExpect(jsonPath("$.skillsImported").value(2))
                .andExpect(jsonPath("$.certificationsImported").value(1))
                .andExpect(jsonPath("$.errors", hasSize(0)));

        org.junit.jupiter.api.Assertions.assertEquals(2, associateSkillRepository.count());
        org.junit.jupiter.api.Assertions.assertEquals(1, certificationRepository.count());

        // idempotent: re-import creates nothing new
        mockMvc.perform(multipart("/api/v1/data/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.associatesCreated").value(0))
                .andExpect(jsonPath("$.certificationsImported").value(0));
        org.junit.jupiter.api.Assertions.assertEquals(2, associateSkillRepository.count());
        org.junit.jupiter.api.Assertions.assertEquals(1, certificationRepository.count());
    }

    @Test
    void importV2_ignoreNovice_skipsNoviceRatings() throws Exception {
        var file = new MockMultipartFile("file", "skillcloud.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", v2Workbook());

        mockMvc.perform(multipart("/api/v1/data/import").file(file).param("ignoreNovice", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skillsImported").value(1));
        org.junit.jupiter.api.Assertions.assertEquals(1, associateSkillRepository.count());
    }

    @Test
    void importV2_dryRun_writesNothing() throws Exception {
        var file = new MockMultipartFile("file", "skillcloud.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", v2Workbook());

        mockMvc.perform(multipart("/api/v1/data/import").file(file).param("dryRun", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(true))
                .andExpect(jsonPath("$.skillsImported").value(2));
        org.junit.jupiter.api.Assertions.assertEquals(0, associateSkillRepository.count());
        org.junit.jupiter.api.Assertions.assertEquals(0, certificationRepository.count());
        org.junit.jupiter.api.Assertions.assertEquals(0, associateRepository.count());
    }

    @Test
    void import_unsupportedFileType_returns400() throws Exception {
        var file = new MockMultipartFile("file", "roster.txt", "text/plain", "hello".getBytes());
        mockMvc.perform(multipart("/api/v1/data/import").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Unsupported")));
    }

    @Test
    void template_roster_returnsCsvWithHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/data/template").param("type", "roster"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("roster-template.csv")))
                .andExpect(content().string(containsString("ASSOCIATE NAME")))
                .andExpect(content().string(containsString("PROJECT")));
    }

    @Test
    void template_skillcloud_returnsXlsx() throws Exception {
        byte[] body = mockMvc.perform(get("/api/v1/data/template").param("type", "skillcloud"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("skillcloud-template.xlsx")))
                .andReturn().getResponse().getContentAsByteArray();
        // .xlsx is a zip archive — must start with the "PK" signature
        org.junit.jupiter.api.Assertions.assertTrue(body.length > 2 && body[0] == 'P' && body[1] == 'K');
    }

    @Test
    void exportXlsx_returnsWorkbook() throws Exception {
        seedOne();
        mockMvc.perform(get("/api/v1/data/export").param("format", "xlsx"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("associates.xlsx")))
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @Test
    void exportCsv_containsRosterRow() throws Exception {
        seedOne();
        mockMvc.perform(get("/api/v1/data/export").param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("associates.csv")))
                .andExpect(content().string(containsString("Priya Sharma")))
                .andExpect(content().string(containsString("Acme Corp")));
    }

    @Test
    void exportPdf_returnsPdfDocument() throws Exception {
        seedOne();
        mockMvc.perform(get("/api/v1/data/export").param("format", "pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("associates.pdf")))
                .andExpect(content().contentType("application/pdf"));
    }

    @Test
    void exportDocx_returnsWordDocument() throws Exception {
        seedOne();
        mockMvc.perform(get("/api/v1/data/export").param("format", "docx"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("associates.docx")))
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
    }

    @Test
    void export_unknownFormat_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/data/export").param("format", "bmp"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void import_overCapacityRow_isRejectedWithError_associateStillImported() throws Exception {
        // Priya is already 100% allocated on an open-ended project.
        var acme = client("Acme Corp");
        var proj = project("ACM-1", "Data Platform", acme);
        var priya = associate("Priya Sharma", "priya.sharma@softility.com",
                com.softility.omivertex.domain.WorkMode.OFFSHORE);
        allocation(priya, proj, true);

        String csv = """
                ASSOCIATE NAME,COMPANY,LOCATION,CUSTOMER,BILLABLE,Project
                Priya Sharma,Softility,OFFSHORE,Meridian Health,B,Patient Portal
                Arjun Rao,Softility,OFFSHORE,Meridian Health,B,Patient Portal
                """;
        var file = new MockMultipartFile("file", "roster.csv", "text/csv", csv.getBytes());

        mockMvc.perform(multipart("/api/v1/data/import").file(file))
                .andExpect(status().isOk())
                // Arjun's allocation is created; Priya's is rejected with a row error
                .andExpect(jsonPath("$.allocationsCreated").value(1))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0]", containsString("maximum is 100%")));

        // Priya still has exactly her original single allocation
        org.junit.jupiter.api.Assertions.assertEquals(1,
                allocationRepository.findByAssociateId(priya.getId()).size());
    }

    @Test
    void importDryRun_overCapacityRow_reportsErrorAndWritesNothing() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-1", "Data Platform", acme);
        var priya = associate("Priya Sharma", "priya.sharma@softility.com",
                com.softility.omivertex.domain.WorkMode.OFFSHORE);
        allocation(priya, proj, true);

        String csv = """
                ASSOCIATE NAME,COMPANY,LOCATION,CUSTOMER,BILLABLE,Project
                Priya Sharma,Softility,OFFSHORE,Meridian Health,B,Patient Portal
                """;
        var file = new MockMultipartFile("file", "roster.csv", "text/csv", csv.getBytes());

        mockMvc.perform(multipart("/api/v1/data/import").file(file).param("dryRun", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0]", containsString("maximum is 100%")));

        // dry run wrote nothing: still 1 allocation, no Meridian client
        org.junit.jupiter.api.Assertions.assertEquals(1, allocationRepository.count());
        org.junit.jupiter.api.Assertions.assertEquals(1, clientRepository.count());
    }

    private void seedOne() {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var dev = associate("Priya Sharma", "priya@softility.com", com.softility.omivertex.domain.WorkMode.OFFSHORE);
        allocation(dev, proj, true);
    }
}
