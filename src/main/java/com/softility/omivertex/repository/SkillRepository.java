package com.softility.omivertex.repository;

import com.softility.omivertex.domain.Skill;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SkillRepository extends JpaRepository<Skill, Long> {

    Optional<Skill> findByNameIgnoreCaseAndCategoryId(String name, Long categoryId);

    Optional<Skill> findFirstByNameIgnoreCase(String name);

    List<Skill> findByCategoryIdOrderByNameAsc(Long categoryId);

    boolean existsByCategoryId(Long categoryId);

    @EntityGraph(attributePaths = "category")
    List<Skill> findAllByOrderByNameAsc();
}
