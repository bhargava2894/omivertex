package com.softility.omivertex.repository;

import com.softility.omivertex.domain.ProfileChangeRequest;
import com.softility.omivertex.domain.ProfileChangeStatus;
import com.softility.omivertex.domain.ProfileChangeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProfileChangeRequestRepository extends JpaRepository<ProfileChangeRequest, Long> {

    boolean existsByAssociateIdAndTypeAndStatus(Long associateId, ProfileChangeType type, ProfileChangeStatus status);

    List<ProfileChangeRequest> findByAssociateIdOrderByCreatedAtDesc(Long associateId);

    @Query("select r from ProfileChangeRequest r join fetch r.associate"
            + " where (:status is null or r.status = :status) order by r.createdAt asc")
    List<ProfileChangeRequest> findAllByStatus(@Param("status") ProfileChangeStatus status);
}
