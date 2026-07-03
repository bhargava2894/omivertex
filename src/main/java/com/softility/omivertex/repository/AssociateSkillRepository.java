package com.softility.omivertex.repository;

import com.softility.omivertex.domain.AssociateSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AssociateSkillRepository extends JpaRepository<AssociateSkill, Long> {

    @Query("""
            select s from AssociateSkill s
            join fetch s.skill sk
            join fetch sk.category
            where s.associate.id = :associateId""")
    List<AssociateSkill> findByAssociateId(@Param("associateId") Long associateId);

    @Query("""
            select s from AssociateSkill s
            join fetch s.associate
            join fetch s.skill sk
            join fetch sk.category""")
    List<AssociateSkill> findAllWithDetails();

    void deleteByAssociateId(Long associateId);

    boolean existsBySkillId(Long skillId);
}
