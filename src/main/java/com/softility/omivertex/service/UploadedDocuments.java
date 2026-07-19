package com.softility.omivertex.service;

import com.softility.omivertex.web.error.BadRequestException;
import org.springframework.web.multipart.MultipartFile;

/** One place for the "uploaded document must be a PDF or Word .docx" rule. */
public final class UploadedDocuments {

    private UploadedDocuments() {
    }

    public static void requirePdfOrDocx(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Uploaded file cannot be empty");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();

        boolean isPdf = contentType.equals("application/pdf") || filename.endsWith(".pdf");
        boolean isDocx = contentType.equals(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                || filename.endsWith(".docx");

        if (!isPdf && !isDocx) {
            throw new BadRequestException(
                    "Unsupported file type. Only PDF (.pdf) and Word (.docx) documents are allowed.");
        }
    }
}
