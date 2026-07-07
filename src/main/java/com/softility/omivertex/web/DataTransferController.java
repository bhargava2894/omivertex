package com.softility.omivertex.web;

import com.softility.omivertex.service.ExportService;
import com.softility.omivertex.service.ImportService;
import com.softility.omivertex.web.dto.ImportSummaryResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/data")
public class DataTransferController {

    private final ImportService importService;
    private final ExportService exportService;

    public DataTransferController(ImportService importService, ExportService exportService) {
        this.importService = importService;
        this.exportService = exportService;
    }

    @PostMapping("/import")
    public ImportSummaryResponse importRoster(@RequestParam("file") MultipartFile file,
                                              @RequestParam(defaultValue = "false") boolean dryRun,
                                              @RequestParam(defaultValue = "false") boolean ignoreNovice) {
        return importService.importRoster(file, dryRun, ignoreNovice);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam String format) {
        ExportService.ExportFile file = exportService.export(format);
        return fileResponse(file);
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> template(@RequestParam String type) {
        return fileResponse(exportService.template(type));
    }

    private ResponseEntity<byte[]> fileResponse(ExportService.ExportFile file) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.bytes());
    }
}
