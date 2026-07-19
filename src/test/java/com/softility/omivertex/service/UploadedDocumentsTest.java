package com.softility.omivertex.service;

import com.softility.omivertex.web.error.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UploadedDocumentsTest {

    @Test
    void acceptsPdfAndDocx() {
        assertDoesNotThrow(() -> UploadedDocuments.requirePdfOrDocx(
                new MockMultipartFile("file", "jd.pdf", "application/pdf", new byte[]{1})));
        assertDoesNotThrow(() -> UploadedDocuments.requirePdfOrDocx(
                new MockMultipartFile("file", "jd.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        new byte[]{1})));
    }

    @Test
    void rejectsEmptyFile() {
        assertThrows(BadRequestException.class, () -> UploadedDocuments.requirePdfOrDocx(
                new MockMultipartFile("file", "jd.pdf", "application/pdf", new byte[]{})));
    }

    @Test
    void rejectsOtherTypes() {
        assertThrows(BadRequestException.class, () -> UploadedDocuments.requirePdfOrDocx(
                new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1})));
    }
}
