package com.softility.omivertex.repository;

import com.softility.omivertex.domain.Associate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssociateRepository extends JpaRepository<Associate, Long> {

    boolean existsByEmailIgnoreCase(String email);
}
