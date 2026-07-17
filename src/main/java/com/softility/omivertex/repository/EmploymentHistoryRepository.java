package com.softility.omivertex.repository;

import com.softility.omivertex.domain.EmploymentHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmploymentHistoryRepository extends JpaRepository<EmploymentHistory, Long> {

    List<EmploymentHistory> findByAssociateIdOrderBySortOrderAsc(Long associateId);

    void deleteByAssociateId(Long associateId);
}
