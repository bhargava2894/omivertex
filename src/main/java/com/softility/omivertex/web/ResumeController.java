package com.softility.omivertex.web;

import com.softility.omivertex.domain.Resume;
import com.softility.omivertex.service.ResumeService;
import com.softility.omivertex.web.dto.ResumeDtos.ParsedResumeResponse;
import com.softility.omivertex.web.dto.ResumeDtos.ResumeMetaResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class ResumeController {

    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @PostMapping("/resumes/parse")
    public ParsedResumeResponse parseResume(@RequestParam("file") MultipartFile file) {
        return resumeService.parse(file);
    }

    @PostMapping("/associates/{id}/resume")
    @ResponseStatus(HttpStatus.CREATED)
    public ResumeMetaResponse uploadResume(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return resumeService.store(id, file);
    }

    @GetMapping("/associates/{id}/resume")
    public ResponseEntity<byte[]> downloadResume(@PathVariable Long id) {
        Resume resume = resumeService.download(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(resume.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resume.getFilename() + "\"")
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .body(resume.getContent());
    }

    @DeleteMapping("/associates/{id}/resume")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteResume(@PathVariable Long id) {
        resumeService.delete(id);
    }
}
