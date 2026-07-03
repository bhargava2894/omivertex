package com.softility.omivertex.repository;

import com.softility.omivertex.domain.SkillCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SkillCategoryRepository extends JpaRepository<SkillCategory, Long> {

    Optional<SkillCategory> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    List<SkillCategory> findAllByOrderByNameAsc();
}
