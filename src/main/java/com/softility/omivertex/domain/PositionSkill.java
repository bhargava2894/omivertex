package com.softility.omivertex.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "position_skills",
        uniqueConstraints = @UniqueConstraint(columnNames = {"position_id", "skill_id"}))
public class PositionSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "position_id", nullable = false)
    private OpenPosition position;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Enumerated(EnumType.STRING)
    private Proficiency minProficiency; // null = any level

    @Column(nullable = false)
    private boolean required = true; // true = must-have, false = nice-to-have

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public OpenPosition getPosition() { return position; }
    public void setPosition(OpenPosition position) { this.position = position; }
    public Skill getSkill() { return skill; }
    public void setSkill(Skill skill) { this.skill = skill; }
    public Proficiency getMinProficiency() { return minProficiency; }
    public void setMinProficiency(Proficiency minProficiency) { this.minProficiency = minProficiency; }
    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }
}
