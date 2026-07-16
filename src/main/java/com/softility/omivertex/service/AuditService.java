package com.softility.omivertex.service;

import com.softility.omivertex.domain.AuditEntry;
import com.softility.omivertex.repository.AuditEntryRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Records who changed what. Called from the service layer on every mutation so
 * the trail is complete regardless of which controller (or import) triggered it.
 */
@Service
public class AuditService {

    private final AuditEntryRepository repository;

    public AuditService(AuditEntryRepository repository) {
        this.repository = repository;
    }

    public void record(String action, String entityType, Long entityId, String summary) {
        AuditEntry entry = new AuditEntry();
        entry.setUsername(currentUsername());
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setSummary(summary.length() > 500 ? summary.substring(0, 500) : summary);
        repository.save(entry);
    }

    /** The acting principal, "system" when unauthenticated — shared with the assistant interaction log. */
    public static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null || !auth.isAuthenticated() ? "system" : auth.getName();
    }
}
