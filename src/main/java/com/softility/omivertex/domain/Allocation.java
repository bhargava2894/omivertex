package com.softility.omivertex.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "allocations")
public class Allocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "associate_id", nullable = false)
    private Associate associate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false)
    private boolean billable;

    @Column(nullable = false)
    private int allocationPercent = 100;

    @Column(nullable = false)
    private LocalDate startDate;

    private LocalDate endDate;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** Current = started on/before today and not yet ended. */
    public boolean isCurrent() {
        LocalDate today = LocalDate.now();
        return !startDate.isAfter(today) && (endDate == null || !endDate.isBefore(today));
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Associate getAssociate() { return associate; }
    public void setAssociate(Associate associate) { this.associate = associate; }
    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }
    public boolean isBillable() { return billable; }
    public void setBillable(boolean billable) { this.billable = billable; }
    public int getAllocationPercent() { return allocationPercent; }
    public void setAllocationPercent(int allocationPercent) { this.allocationPercent = allocationPercent; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public Instant getCreatedAt() { return createdAt; }
}
