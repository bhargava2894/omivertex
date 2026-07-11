package com.softility.omivertex.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "open_positions")
public class OpenPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    private String requiredSkill;

    @Enumerated(EnumType.STRING)
    private WorkMode workMode; // null = any

    @Column(nullable = false)
    private boolean billable = true;

    @Column(nullable = false)
    private int allocationPercent = 100;

    private LocalDate startDate;

    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PositionStatus status = PositionStatus.OPEN;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }
    public String getRequiredSkill() { return requiredSkill; }
    public void setRequiredSkill(String requiredSkill) { this.requiredSkill = requiredSkill; }
    public WorkMode getWorkMode() { return workMode; }
    public void setWorkMode(WorkMode workMode) { this.workMode = workMode; }
    public boolean isBillable() { return billable; }
    public void setBillable(boolean billable) { this.billable = billable; }
    public int getAllocationPercent() { return allocationPercent; }
    public void setAllocationPercent(int allocationPercent) { this.allocationPercent = allocationPercent; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public PositionStatus getStatus() { return status; }
    public void setStatus(PositionStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
}
