package com.softility.omivertex.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "associates", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
public class Associate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String company;

    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkMode workMode;

    private String designation;

    @Column(name = "employee_id", unique = true)
    private String employeeId;

    // Résumé-extracted contact number; not required at intake.
    @Column(length = 32)
    private String phone;

    // Day the person joined the company. Anchors the bench clock for associates who
    // have never been allocated; without it, re-importing a roster would reset
    // everyone's bench age to the row's creation date.
    private LocalDate joinedDate;

    // Exit lifecycle: set together (reason + last working day). Once the last working
    // day has passed, AssociateService.processExits() flips status to INACTIVE and
    // closes open allocations — see docs/TECHNICAL.md business rules.
    private LocalDate resignationDate;

    private LocalDate lastWorkingDay;

    @Enumerated(EnumType.STRING)
    private ExitReason exitReason;

    // Informal free-text "headline" skills only: a roster quick-glance value and the
    // target of the CSV SKILL column on import. The authoritative skill model is the
    // structured AssociateSkill graph (proficiency-rated, category-organized), which
    // powers search, reports, and demand matching. These two fields are intentionally
    // NOT that system; PositionService uses them only as a text-match fallback when a
    // position has no structured required skill. Do not build new features on them.
    private String primarySkill;

    private String secondarySkill;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EntityStatus status = EntityStatus.ACTIVE;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public WorkMode getWorkMode() { return workMode; }
    public void setWorkMode(WorkMode workMode) { this.workMode = workMode; }
    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public LocalDate getJoinedDate() { return joinedDate; }
    public void setJoinedDate(LocalDate joinedDate) { this.joinedDate = joinedDate; }
    public LocalDate getResignationDate() { return resignationDate; }
    public void setResignationDate(LocalDate resignationDate) { this.resignationDate = resignationDate; }
    public LocalDate getLastWorkingDay() { return lastWorkingDay; }
    public void setLastWorkingDay(LocalDate lastWorkingDay) { this.lastWorkingDay = lastWorkingDay; }
    public ExitReason getExitReason() { return exitReason; }
    public void setExitReason(ExitReason exitReason) { this.exitReason = exitReason; }
    public String getPrimarySkill() { return primarySkill; }
    public void setPrimarySkill(String primarySkill) { this.primarySkill = primarySkill; }
    public String getSecondarySkill() { return secondarySkill; }
    public void setSecondarySkill(String secondarySkill) { this.secondarySkill = secondarySkill; }
    public EntityStatus getStatus() { return status; }
    public void setStatus(EntityStatus status) { this.status = status; }
    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
    public Instant getCreatedAt() { return createdAt; }
}
