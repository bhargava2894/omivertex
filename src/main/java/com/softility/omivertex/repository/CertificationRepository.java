package com.softility.omivertex.repository;

import com.softility.omivertex.domain.Certification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CertificationRepository extends JpaRepository<Certification, Long> {

    List<Certification> findByAssociateIdOrderByExpiryDateAsc(Long associateId);

    @Query("select c from Certification c join fetch c.associate order by c.expiryDate asc nulls last")
    List<Certification> findAllWithAssociate();

    void deleteByAssociateId(Long associateId);
}
