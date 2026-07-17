package com.softility.omivertex.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * One PREVIOUS (external) employer from an associate's résumé. Internal
 * Softility engagement history is allocation-derived and never stored here.
 */
@Entity
@Table(name = "employment_history")
public class EmploymentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "associate_id", nullable = false)
    private Long associateId;

    @Column(nullable = false, length = 120)
    private String company;

    @Column(length = 120)
    private String title;

    private LocalDate startDate;
    private LocalDate endDate;

    /** Résumé order, 0 = topmost (most recent) as extracted. */
    @Column(nullable = false)
    private int sortOrder;

    public Long getId() { return id; }
    public Long getAssociateId() { return associateId; }
    public void setAssociateId(Long associateId) { this.associateId = associateId; }
    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
