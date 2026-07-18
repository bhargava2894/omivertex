package com.softility.omivertex.api;

import com.softility.omivertex.domain.Associate;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ResumeApiTest extends ApiTestBase {

    /** Mockito boolean default (false) keeps every pre-existing test on the keyword path. */
    @MockBean GeminiClient geminiClient;

    private byte[] createPdf(String text) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.beginText();
                contentStream.newLineAtOffset(100, 700);
                contentStream.showText(text);
                contentStream.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void parseResume_returnsStatelessSuggestions() throws Exception {
        skill("Programming & Scripting", "Java");
        skill("Cloud Platforms", "AWS");

        byte[] pdfBytes = createPdf("I am a developer skilled in Java and AWS.");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.pdf",
                "application/pdf",
                pdfBytes
        );

        asyncPerform(multipart("/api/v1/resumes/parse").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.textExtracted").value(true))
                .andExpect(jsonPath("$.suggestedSkills", hasSize(2)))
                .andExpect(jsonPath("$.suggestedSkills[?(@.skillName=='Java')].categoryName").value("Programming & Scripting"))
                .andExpect(jsonPath("$.suggestedSkills[?(@.skillName=='AWS')].categoryName").value("Cloud Platforms"));
    }

    @Test
    void parseResume_invalidFileType_returnsBadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "photo.png",
                "image/png",
                new byte[]{1, 2, 3, 4}
        );

        asyncPerform(multipart("/api/v1/resumes/parse").file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadResume_persistsBlobAndAudits() throws Exception {
        Associate assoc = associate("Rahul Verma", "rahul@softility.com", WorkMode.OFFSHORE);

        byte[] pdfBytes = createPdf("PDF Content");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.pdf",
                "application/pdf",
                pdfBytes
        );

        mockMvc.perform(multipart("/api/v1/associates/" + assoc.getId() + "/resume").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.filename").value("resume.pdf"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"));

        assertTrue(resumeRepository.existsByAssociateId(assoc.getId()));

        var audit = auditEntryRepository.findAll().stream()
                .filter(a -> a.getEntityId().equals(assoc.getId()))
                .findFirst();
        assertTrue(audit.isPresent());
        assertEquals("UPDATED", audit.get().getAction());
        assertTrue(audit.get().getSummary().contains("Uploaded résumé"));
    }

    @Test
    void uploadResume_replacesExistingResume() throws Exception {
        Associate assoc = associate("Amit Sharma", "amit@softility.com", WorkMode.OFFSHORE);

        byte[] pdfBytes1 = createPdf("PDF Content 1");
        byte[] pdfBytes2 = createPdf("PDF Content 2");

        MockMultipartFile file1 = new MockMultipartFile(
                "file",
                "resume1.pdf",
                "application/pdf",
                pdfBytes1
        );

        MockMultipartFile file2 = new MockMultipartFile(
                "file",
                "resume2.pdf",
                "application/pdf",
                pdfBytes2
        );

        // First upload
        mockMvc.perform(multipart("/api/v1/associates/" + assoc.getId() + "/resume").file(file1))
                .andExpect(status().isCreated());

        // Second upload should replace
        mockMvc.perform(multipart("/api/v1/associates/" + assoc.getId() + "/resume").file(file2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.filename").value("resume2.pdf"));

        assertEquals(1, resumeRepository.count());
        var saved = resumeRepository.findByAssociateId(assoc.getId()).get();
        assertEquals("resume2.pdf", saved.getFilename());
    }

    @Test
    void downloadResume_returnsFileBytes() throws Exception {
        Associate assoc = associate("Sneha Patel", "sneha@softility.com", WorkMode.OFFSHORE);

        byte[] pdfBytes = createPdf("PDF Content");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.pdf",
                "application/pdf",
                pdfBytes
        );

        mockMvc.perform(multipart("/api/v1/associates/" + assoc.getId() + "/resume").file(file))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/associates/" + assoc.getId() + "/resume"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"resume.pdf\""))
                .andExpect(content().bytes(pdfBytes));
    }

    @Test
    void downloadResume_nonExistent_returnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/associates/9999/resume"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteResume_removesFromDbAndAudits() throws Exception {
        Associate assoc = associate("Priya Rao", "priya@softility.com", WorkMode.OFFSHORE);

        byte[] pdfBytes = createPdf("PDF Content");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.pdf",
                "application/pdf",
                pdfBytes
        );

        mockMvc.perform(multipart("/api/v1/associates/" + assoc.getId() + "/resume").file(file))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/associates/" + assoc.getId() + "/resume"))
                .andExpect(status().isNoContent());

        assertTrue(resumeRepository.findByAssociateId(assoc.getId()).isEmpty());

        var audit = auditEntryRepository.findAll().stream()
                .filter(a -> a.getEntityId().equals(assoc.getId()) && a.getAction().equals("DELETED"))
                .findFirst();
        assertTrue(audit.isPresent());
        assertTrue(audit.get().getSummary().contains("Deleted résumé"));
    }

    @Test
    @WithMockUser(username = "viewer", roles = "VIEWER")
    void mutations_byViewer_returnsForbidden() throws Exception {
        Associate assoc = associate("Karan Johar", "karan@softility.com", WorkMode.OFFSHORE);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.pdf",
                "application/pdf",
                "PDF Content".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/associates/" + assoc.getId() + "/resume").file(file))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/associates/" + assoc.getId() + "/resume"))
                .andExpect(status().isForbidden());
    }

    @Test
    void parseResume_usesAiExtraction_whenConfigured() throws Exception {
        var java = skill("Backend", "Java");
        when(geminiClient.isConfigured()).thenReturn(true);
        when(geminiClient.extractResume(anyString(), anyList()))
                .thenReturn(new GeminiClient.ResumeExtraction(
                        List.of(new GeminiClient.ExtractedSkill(java.getId(), Proficiency.ADVANCE,
                                "led a Java microservices team")),
                        "8 years of experience, most recently a senior backend engineer.",
                        null, null, List.of()));

        asyncPerform(multipart("/api/v1/resumes/parse")
                        .file(new MockMultipartFile("file", "resume.pdf", "application/pdf",
                                createPdf("Java expert"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("AI"))
                .andExpect(jsonPath("$.experienceSummary").value(
                        "8 years of experience, most recently a senior backend engineer."))
                .andExpect(jsonPath("$.suggestedSkills", hasSize(1)))
                .andExpect(jsonPath("$.suggestedSkills[0].skillName").value("Java"))
                .andExpect(jsonPath("$.suggestedSkills[0].proficiency").value("ADVANCE"))
                .andExpect(jsonPath("$.suggestedSkills[0].evidence").value("led a Java microservices team"));
    }

    @Test
    void parseResume_fallsBackToKeyword_whenGeminiFails() throws Exception {
        skill("Backend", "Java");
        when(geminiClient.isConfigured()).thenReturn(true);
        when(geminiClient.extractResume(anyString(), anyList()))
                .thenThrow(new RuntimeException("upstream 500"));

        asyncPerform(multipart("/api/v1/resumes/parse")
                        .file(new MockMultipartFile("file", "resume.pdf", "application/pdf",
                                createPdf("Java expert"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("KEYWORD"))
                .andExpect(jsonPath("$.suggestedSkills", hasSize(1)))
                .andExpect(jsonPath("$.suggestedSkills[0].skillName").value("Java"));
    }

    @Test
    void parseMyResume_associateAllowed_viaMeEndpoint() throws Exception {
        skill("Backend", "Java");
        // not configured -> keyword path; the point is the ASSOCIATE-role route works
        asyncPerform(multipart("/api/v1/me/resumes/parse")
                        .file(new MockMultipartFile("file", "resume.pdf", "application/pdf",
                                createPdf("Java expert")))
                        .with(SecurityMockMvcRequestPostProcessors.user("a@softility.com").roles("ASSOCIATE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("KEYWORD"));
    }
}
