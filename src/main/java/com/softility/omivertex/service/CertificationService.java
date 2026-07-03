package com.softility.omivertex.service;

import com.softility.omivertex.domain.Associate;
import com.softility.omivertex.domain.Certification;
import com.softility.omivertex.repository.AssociateRepository;
import com.softility.omivertex.repository.CertificationRepository;
import com.softility.omivertex.web.dto.CertificationDtos.CertificationRequest;
import com.softility.omivertex.web.dto.CertificationDtos.CertificationResponse;
import com.softility.omivertex.web.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.List;

@Service
@Transactional
public class CertificationService {

    private final CertificationRepository certifications;
    private final AssociateRepository associates;
    private final AuditService auditService;

    public CertificationService(CertificationRepository certifications, AssociateRepository associates,
                                AuditService auditService) {
        this.certifications = certifications;
        this.associates = associates;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<CertificationResponse> listForAssociate(Long associateId) {
        if (!associates.existsById(associateId)) {
            throw new NotFoundException("Associate", associateId);
        }
        return certifications.findByAssociateIdOrderByExpiryDateAsc(associateId).stream()
                .map(CertificationResponse::from).toList();
    }

    /** Org-wide list, soonest expiry first; q matches name, authority or associate name. */
    @Transactional(readOnly = true)
    public List<CertificationResponse> listAll(String q) {
        String needle = q == null ? "" : q.toLowerCase(Locale.ROOT).trim();
        return certifications.findAllWithAssociate().stream()
                .filter(c -> needle.isEmpty()
                        || contains(c.getName(), needle)
                        || contains(c.getAuthority(), needle)
                        || contains(c.getCredentialId(), needle)
                        || contains(c.getAssociate().getName(), needle))
                .map(CertificationResponse::from)
                .toList();
    }

    public CertificationResponse create(Long associateId, CertificationRequest request) {
        Associate associate = associates.findById(associateId)
                .orElseThrow(() -> new NotFoundException("Associate", associateId));
        Certification cert = new Certification();
        cert.setAssociate(associate);
        cert.setName(request.name());
        cert.setAuthority(request.authority());
        cert.setCredentialId(request.credentialId());
        cert.setIssuedDate(request.issuedDate());
        cert.setExpiryDate(request.expiryDate());
        cert = certifications.save(cert);
        auditService.record("CREATED", "Certification", cert.getId(),
                "Added certification " + cert.getName() + " for " + associate.getName());
        return CertificationResponse.from(cert);
    }

    public void delete(Long id) {
        Certification cert = certifications.findById(id)
                .orElseThrow(() -> new NotFoundException("Certification", id));
        auditService.record("DELETED", "Certification", id,
                "Removed certification " + cert.getName() + " of " + cert.getAssociate().getName());
        certifications.delete(cert);
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }
}
