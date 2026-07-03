package com.softility.omivertex.repository;

import com.softility.omivertex.domain.AuditEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {

    List<AuditEntry> findByEntityTypeOrderByIdDesc(String entityType, Pageable pageable);

    List<AuditEntry> findAllByOrderByIdDesc(Pageable pageable);
}
