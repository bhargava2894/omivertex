package com.softility.omivertex.web.dto;

import com.softility.omivertex.domain.Certification;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public final class CertificationDtos {

    private CertificationDtos() {
    }

    public record CertificationRequest(
            @NotBlank(message = "Name is required") String name,
            String authority,
            String credentialId,
            LocalDate issuedDate,
            LocalDate expiryDate) {
    }

    public record CertificationResponse(
            Long id,
            Long associateId,
            String associateName,
            String name,
            String authority,
            String credentialId,
            LocalDate issuedDate,
            LocalDate expiryDate) {

        public static CertificationResponse from(Certification c) {
            return new CertificationResponse(c.getId(), c.getAssociate().getId(), c.getAssociate().getName(),
                    c.getName(), c.getAuthority(), c.getCredentialId(), c.getIssuedDate(), c.getExpiryDate());
        }
    }
}
