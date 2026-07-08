package com.softility.omivertex.domain;

import jakarta.persistence.*;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * An associate's stored résumé. `associateId` is a plain column (not a JPA
 * relationship) with a unique constraint — one résumé per associate; the service
 * manages replace-on-upload by id. The blob is LAZY so metadata reads (via the
 * ResumeMeta projection) never pull the bytes.
 */
@Entity
@Table(name = "resumes", uniqueConstraints = @UniqueConstraint(columnNames = "associate_id"))
public class Resume {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "associate_id", nullable = false, unique = true)
    private Long associateId;

    @Column(nullable = false)
    private String filename;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "byte_size", nullable = false)
    private long byteSize;

    // Hibernate's default @Lob byte[] mapping emits a "blob" column, which H2's
    // PostgreSQL-compat mode (used by tests) doesn't support, and which mismatches
    // the BYTEA column from V3__add_resumes_table.sql under real Postgres. Pinning
    // the JDBC type to VARBINARY makes both agree (H2: VARBINARY, Postgres: bytea).
    @Lob
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false)
    private byte[] content;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAssociateId() { return associateId; }
    public void setAssociateId(Long associateId) { this.associateId = associateId; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public long getByteSize() { return byteSize; }
    public void setByteSize(long byteSize) { this.byteSize = byteSize; }
    public byte[] getContent() { return content; }
    public void setContent(byte[] content) { this.content = content; }
    public Instant getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(Instant uploadedAt) { this.uploadedAt = uploadedAt; }
}
