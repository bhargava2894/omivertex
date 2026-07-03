package com.softility.omivertex.repository;

import com.softility.omivertex.domain.OpenPosition;
import com.softility.omivertex.domain.PositionStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OpenPositionRepository extends JpaRepository<OpenPosition, Long> {

    @Query("""
            select p from OpenPosition p
            join fetch p.project pr
            join fetch pr.client
            order by p.createdAt desc""")
    List<OpenPosition> findAllWithDetails();

    long countByStatus(PositionStatus status);

    boolean existsByProjectId(Long projectId);
}
