package com.softility.omivertex.repository;

import com.softility.omivertex.domain.PositionSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PositionSkillRepository extends JpaRepository<PositionSkill, Long> {

    @Query("select ps from PositionSkill ps join fetch ps.skill s join fetch s.category where ps.position.id = :positionId")
    List<PositionSkill> findByPositionId(Long positionId);

    @Modifying
    void deleteByPositionId(Long positionId);

    @Query("select ps from PositionSkill ps join fetch ps.skill s join fetch s.category join fetch ps.position")
    List<PositionSkill> findAllWithDetails();
}
