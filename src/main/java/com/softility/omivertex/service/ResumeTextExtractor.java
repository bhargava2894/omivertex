package com.softility.omivertex.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;

@Service
public class ResumeTextExtractor {

    public String extractText(byte[] bytes, String contentType, String filename) {
        if (contentType == null) {
            contentType = "";
        }
        if (filename == null) {
            filename = "";
        }

        String lowerContentType = contentType.toLowerCase();
        String lowerFilename = filename.toLowerCase();

        try {
            if (lowerContentType.contains("pdf") || lowerFilename.endsWith(".pdf")) {
                return extractPdf(bytes);
            } else if (lowerContentType.contains("word") || lowerContentType.contains("officedocument.wordprocessingml") || lowerFilename.endsWith(".docx")) {
                return extractDocx(bytes);
            } else if (lowerContentType.contains("text") || lowerFilename.endsWith(".txt")) {
                return new String(bytes);
            } else {
                throw new IllegalArgumentException("Unsupported file type. Please upload a PDF (.pdf) or Word (.docx) document.");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            // Handle corrupt or encrypted files gracefully by returning an empty string
            return "";
        }
    }

    private String extractPdf(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(bytes))) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private String extractDocx(byte[] bytes) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            try (XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
                return extractor.getText();
            }
        }
    }
}
