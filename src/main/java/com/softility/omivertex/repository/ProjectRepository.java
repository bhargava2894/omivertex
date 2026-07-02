package com.softility.omivertex.repository;

import com.softility.omivertex.domain.Project;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    boolean existsByCodeIgnoreCase(String code);

    @EntityGraph(attributePaths = "client")
    List<Project> findByClientId(Long clientId);

    @EntityGraph(attributePaths = "client")
    List<Project> findAllByOrderByNameAsc();

    boolean existsByClientId(Long clientId);

    java.util.Optional<Project> findByNameIgnoreCaseAndClientId(String name, Long clientId);
}
