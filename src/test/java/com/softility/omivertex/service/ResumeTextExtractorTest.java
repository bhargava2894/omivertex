package com.softility.omivertex.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class ResumeTextExtractorTest {

    private final ResumeTextExtractor extractor = new ResumeTextExtractor();

    @Test
    void extractText_pdf_extractsContent() throws IOException {
        byte[] pdfBytes;
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.beginText();
                contentStream.newLineAtOffset(100, 700);
                contentStream.showText("Experienced Java and AWS Developer");
                contentStream.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            pdfBytes = out.toByteArray();
        }

        String text = extractor.extractText(pdfBytes, "application/pdf", "resume.pdf");
        assertNotNull(text);
        assertTrue(text.contains("Experienced Java and AWS Developer"));
    }

    @Test
    void extractText_docx_extractsContent() throws IOException {
        byte[] docxBytes;
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph p = doc.createParagraph();
            XWPFRun r = p.createRun();
            r.setText("Experienced Java and AWS Developer");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            docxBytes = out.toByteArray();
        }

        String text = extractor.extractText(docxBytes, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "resume.docx");
        assertNotNull(text);
        assertTrue(text.contains("Experienced Java and AWS Developer"));
    }

    @Test
    void extractText_txt_extractsContent() {
        byte[] txtBytes = "Experienced Java and AWS Developer".getBytes();
        String text = extractor.extractText(txtBytes, "text/plain", "resume.txt");
        assertEquals("Experienced Java and AWS Developer", text);
    }

    @Test
    void extractText_corruptBytes_returnsEmptyString() {
        byte[] corruptBytes = new byte[]{1, 2, 3, 4};
        String text = extractor.extractText(corruptBytes, "application/pdf", "resume.pdf");
        assertEquals("", text);
    }

    @Test
    void extractText_unsupportedType_throwsException() {
        byte[] bytes = new byte[]{1, 2, 3, 4};
        assertThrows(IllegalArgumentException.class, () ->
            extractor.extractText(bytes, "image/png", "photo.png")
        );
    }
}
