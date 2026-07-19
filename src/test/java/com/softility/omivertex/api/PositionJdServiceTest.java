package com.softility.omivertex.api;

import com.softility.omivertex.domain.Client;
import com.softility.omivertex.domain.Project;
import com.softility.omivertex.domain.Skill;
import com.softility.omivertex.domain.WorkMode;
import com.softility.omivertex.service.GeminiClient;
import com.softility.omivertex.service.PositionService;
import com.softility.omivertex.web.dto.PositionJdDtos.ParsedJobDescriptionResponse;
import com.softility.omivertex.web.dto.ResumeDtos.SuggestionSource;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class PositionJdServiceTest extends ApiTestBase {

    @MockBean GeminiClient geminiClient;
    @Autowired PositionService positionService;

    /** A real one-page PDF so ResumeTextExtractor returns non-blank text end-to-end. */
    private MockMultipartFile pdf(String text) throws IOException {
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
            return new MockMultipartFile("file", "jd.pdf", "application/pdf", out.toByteArray());
        }
    }

    @Test
    void aiExtraction_mapsSkillsUnmatchedAndProject() throws IOException {
        Client acme = client("Acme Corp");
        Project proj = project("ACM-200", "Mobile App", acme);
        Skill java = skill("Backend", "Java");

        when(geminiClient.isConfigured()).thenReturn(true);
        when(geminiClient.extractJobDescription(anyString(), anyList(), anyList()))
                .thenReturn(new GeminiClient.JobDescriptionExtraction(
                        "Senior Java Developer",
                        List.of(new GeminiClient.ExtractedSkill(java.getId(),
                                com.softility.omivertex.domain.Proficiency.ADVANCE, "")),
                        List.of("Rust"),
                        "Build and run backend services.",
                        WorkMode.ONSHORE, 80, null, null,
                        "Acme Corp · Mobile App"));

        ParsedJobDescriptionResponse res =
                positionService.parseJobDescription(pdf("Java backend role at Acme Mobile App"));

        assertEquals("Senior Java Developer", res.title());
        assertEquals(SuggestionSource.AI, res.source());
        assertEquals(1, res.skills().size());
        assertEquals("Java", res.skills().get(0).skillName());
        assertTrue(res.skills().get(0).required());
        assertEquals(List.of("Rust"), res.unmatchedSkills());
        assertEquals(WorkMode.ONSHORE, res.workMode());
        assertEquals(80, res.allocationPercent());
        assertEquals(proj.getId(), res.suggestedProjectId());
        assertEquals("Acme Corp · Mobile App", res.suggestedProjectName());
    }

    @Test
    void rejectsUnsupportedFileType() {
        MockMultipartFile png = new MockMultipartFile("file", "x.png", "image/png", new byte[]{1});
        assertThrows(com.softility.omivertex.web.error.BadRequestException.class,
                () -> positionService.parseJobDescription(png));
    }
}
