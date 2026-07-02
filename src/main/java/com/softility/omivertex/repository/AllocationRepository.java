package com.softility.omivertex.repository;

import com.softility.omivertex.domain.Allocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface AllocationRepository extends JpaRepository<Allocation, Long> {

    boolean existsByAssociateIdAndProjectIdAndEndDateIsNull(Long associateId, Long projectId);

    @Query("""
            select a from Allocation a
            join fetch a.associate
            join fetch a.project p
            join fetch p.client
            order by a.startDate desc""")
    List<Allocation> findAllWithDetails();

    @Query("""
            select a from Allocation a
            join fetch a.associate
            join fetch a.project p
            join fetch p.client
            where a.startDate <= :today and (a.endDate is null or a.endDate >= :today)""")
    List<Allocation> findCurrent(@Param("today") LocalDate today);

    List<Allocation> findByAssociateId(Long associateId);

    boolean existsByProjectId(Long projectId);

    boolean existsByAssociateId(Long associateId);
}
