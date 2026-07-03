package com.softility.omivertex.web;

import com.softility.omivertex.service.CertificationService;
import com.softility.omivertex.web.dto.CertificationDtos.CertificationRequest;
import com.softility.omivertex.web.dto.CertificationDtos.CertificationResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class CertificationController {

    private final CertificationService certificationService;

    public CertificationController(CertificationService certificationService) {
        this.certificationService = certificationService;
    }

    @GetMapping("/associates/{associateId}/certifications")
    public List<CertificationResponse> listForAssociate(@PathVariable Long associateId) {
        return certificationService.listForAssociate(associateId);
    }

    @PostMapping("/associates/{associateId}/certifications")
    @ResponseStatus(HttpStatus.CREATED)
    public CertificationResponse create(@PathVariable Long associateId,
                                        @Valid @RequestBody CertificationRequest request) {
        return certificationService.create(associateId, request);
    }

    @GetMapping("/certifications")
    public List<CertificationResponse> listAll(@RequestParam(required = false) String q) {
        return certificationService.listAll(q);
    }

    @DeleteMapping("/certifications/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        certificationService.delete(id);
    }
}
