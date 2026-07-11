package com.softility.omivertex.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * An associate's proposed profile edit (skills or resume). The live profile
 * changes only when an admin approves; the proposal is stored here until then.
 */
@Entity
@Table(name = "profile_change_requests")
public class ProfileChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "associate_id", nullable = false)
    private Associate associate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProfileChangeType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProfileChangeStatus status = ProfileChangeStatus.PENDING;

    /** JSON payload of SkillAssignmentRequest; SKILLS requests only. */
    @Column(columnDefinition = "text")
    private String skillsPayload;

    // RESUME requests only
    private String resumeFilename;
    private String resumeContentType;
    private Long resumeByteSize;

    // Same VARBINARY pinning as Resume.content: H2's PostgreSQL-compat mode has no
    // BLOB type and real Postgres uses BYTEA — this mapping satisfies both.
    @Lob
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARBINARY)
    @Basic(fetch = FetchType.LAZY)
    @Column(columnDefinition = "bytea")
    private byte[] resumeContent;

    private String note;
    private String decidedBy;
    private Instant decidedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Associate getAssociate() { return associate; }
    public void setAssociate(Associate associate) { this.associate = associate; }
    public ProfileChangeType getType() { return type; }
    public void setType(ProfileChangeType type) { this.type = type; }
    public ProfileChangeStatus getStatus() { return status; }
    public void setStatus(ProfileChangeStatus status) { this.status = status; }
    public String getSkillsPayload() { return skillsPayload; }
    public void setSkillsPayload(String skillsPayload) { this.skillsPayload = skillsPayload; }
    public String getResumeFilename() { return resumeFilename; }
    public void setResumeFilename(String resumeFilename) { this.resumeFilename = resumeFilename; }
    public String getResumeContentType() { return resumeContentType; }
    public void setResumeContentType(String resumeContentType) { this.resumeContentType = resumeContentType; }
    public Long getResumeByteSize() { return resumeByteSize; }
    public void setResumeByteSize(Long resumeByteSize) { this.resumeByteSize = resumeByteSize; }
    public byte[] getResumeContent() { return resumeContent; }
    public void setResumeContent(byte[] resumeContent) { this.resumeContent = resumeContent; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getDecidedBy() { return decidedBy; }
    public void setDecidedBy(String decidedBy) { this.decidedBy = decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
