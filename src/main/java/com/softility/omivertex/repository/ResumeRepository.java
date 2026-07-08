package com.softility.omivertex.repository;

import com.softility.omivertex.domain.Resume;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface ResumeRepository extends JpaRepository<Resume, Long> {

    Optional<Resume> findByAssociateId(Long associateId);

    boolean existsByAssociateId(Long associateId);

    // Derived delete queries execute a remove() directly and need an active
    // transaction; ResumeService's callers are already @Transactional, but this
    // repository test invokes it standalone, so the method needs its own boundary.
    @Transactional
    void deleteByAssociateId(Long associateId);

    /** Metadata only — never selects the `content` blob. */
    @Query("select r.filename as filename, r.contentType as contentType, "
            + "r.byteSize as byteSize, r.uploadedAt as uploadedAt "
            + "from Resume r where r.associateId = :associateId")
    Optional<ResumeMeta> findMetaByAssociateId(Long associateId);

    interface ResumeMeta {
        String getFilename();
        String getContentType();
        long getByteSize();
        Instant getUploadedAt();
    }
}
