package com.softility.omivertex.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "associate_skills", uniqueConstraints = @UniqueConstraint(columnNames = {"associate_id", "skill_id"}))
public class AssociateSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "associate_id", nullable = false)
    private Associate associate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Proficiency proficiency;

    // Exactly one of an associate's skills may be primary (the roster headline);
    // enforced in AssociateService. Column is `is_primary` to dodge the SQL reserved word.
    @Column(name = "is_primary", nullable = false)
    private boolean primary = false;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Associate getAssociate() { return associate; }
    public void setAssociate(Associate associate) { this.associate = associate; }
    public Skill getSkill() { return skill; }
    public void setSkill(Skill skill) { this.skill = skill; }
    public Proficiency getProficiency() { return proficiency; }
    public void setProficiency(Proficiency proficiency) { this.proficiency = proficiency; }
    public boolean isPrimary() { return primary; }
    public void setPrimary(boolean primary) { this.primary = primary; }
}
