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
    void import_unsupportedFileType_returns400() throws Exception {
        var file = new MockMultipartFile("file", "roster.txt", "text/plain", "hello".getBytes());
        mockMvc.perform(multipart("/api/v1/data/import").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Unsupported")));
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

    private void seedOne() {
        var acme = client("Acme Corp");
        var proj = project("ACM-100", "Storefront Revamp", acme);
        var dev = associate("Priya Sharma", "priya@softility.com", com.softility.omivertex.domain.WorkMode.OFFSHORE);
        allocation(dev, proj, true);
    }
}
