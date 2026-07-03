package com.softility.omivertex.web;

import com.softility.omivertex.domain.AuditEntry;
import com.softility.omivertex.repository.AuditEntryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/audit")
public class AuditController {

    public record AuditEntryResponse(Long id, String username, String action,
                                     String entityType, Long entityId, String summary, Instant timestamp) {

        static AuditEntryResponse from(AuditEntry e) {
            return new AuditEntryResponse(e.getId(), e.getUsername(), e.getAction(),
                    e.getEntityType(), e.getEntityId(), e.getSummary(), e.getTimestamp());
        }
    }

    private final AuditEntryRepository repository;

    public AuditController(AuditEntryRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<AuditEntryResponse> list(@RequestParam(required = false) String entityType,
                                         @RequestParam(defaultValue = "100") int limit) {
        var page = PageRequest.of(0, Math.min(Math.max(limit, 1), 500));
        var entries = entityType == null || entityType.isBlank()
                ? repository.findAllByOrderByIdDesc(page)
                : repository.findByEntityTypeOrderByIdDesc(entityType, page);
        return entries.stream().map(AuditEntryResponse::from).toList();
    }
}
