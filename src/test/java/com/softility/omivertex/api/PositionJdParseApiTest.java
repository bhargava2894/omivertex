package com.softility.omivertex.api;

import com.softility.omivertex.domain.Proficiency;
import com.softility.omivertex.domain.WorkMode;
import com.softility.omivertex.service.GeminiClient;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PositionJdParseApiTest extends ApiTestBase {

    @MockBean GeminiClient geminiClient;

    private byte[] pdf(String text) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.beginText();
                cs.newLineAtOffset(100, 700);
                cs.showText(text);
                cs.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void parseJd_aiExtraction_prefillsFields() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-200", "Mobile App", acme);
        var java = skill("Backend", "Java");

        when(geminiClient.isConfigured()).thenReturn(true);
        when(geminiClient.extractJobDescription(anyString(), anyList(), anyList()))
                .thenReturn(new GeminiClient.JobDescriptionExtraction(
                        "Senior Java Developer",
                        List.of(new GeminiClient.ExtractedSkill(java.getId(), Proficiency.ADVANCE, "")),
                        List.of("Rust"),
                        "Build backend services.",
                        WorkMode.ONSHORE, 80, null, null,
                        "Acme Corp · Mobile App"));

        asyncPerform(multipart("/api/v1/positions/parse-jd")
                        .file(new MockMultipartFile("file", "jd.pdf", "application/pdf",
                                pdf("Java backend role at Acme Mobile App"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("AI"))
                .andExpect(jsonPath("$.title").value("Senior Java Developer"))
                .andExpect(jsonPath("$.skills", hasSize(1)))
                .andExpect(jsonPath("$.skills[0].skillName").value("Java"))
                .andExpect(jsonPath("$.skills[0].required").value(true))
                .andExpect(jsonPath("$.unmatchedSkills[0]").value("Rust"))
                .andExpect(jsonPath("$.workMode").value("ONSHORE"))
                .andExpect(jsonPath("$.allocationPercent").value(80))
                .andExpect(jsonPath("$.suggestedProjectId").value(proj.getId()))
                .andExpect(jsonPath("$.suggestedProjectName").value("Acme Corp · Mobile App"));
    }

    @Test
    void parseJd_notConfigured_fallsBackToKeywordSkills() throws Exception {
        skill("Backend", "Java");
        when(geminiClient.isConfigured()).thenReturn(false);

        asyncPerform(multipart("/api/v1/positions/parse-jd")
                        .file(new MockMultipartFile("file", "jd.pdf", "application/pdf",
                                pdf("We need a Java developer"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("KEYWORD"))
                .andExpect(jsonPath("$.title").value((String) null))
                .andExpect(jsonPath("$.skills", hasSize(1)))
                .andExpect(jsonPath("$.skills[0].skillName").value("Java"));
    }

    @Test
    void parseJd_geminiThrows_fallsBackNo500() throws Exception {
        skill("Backend", "Java");
        when(geminiClient.isConfigured()).thenReturn(true);
        when(geminiClient.extractJobDescription(anyString(), anyList(), anyList()))
                .thenThrow(new RuntimeException("upstream 500"));

        asyncPerform(multipart("/api/v1/positions/parse-jd")
                        .file(new MockMultipartFile("file", "jd.pdf", "application/pdf",
                                pdf("We need a Java developer"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("KEYWORD"));
    }

    @Test
    void parseJd_invalidFileType_returns400() throws Exception {
        asyncPerform(multipart("/api/v1/positions/parse-jd")
                        .file(new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1, 2, 3})))
                .andExpect(status().isBadRequest());
    }
}
