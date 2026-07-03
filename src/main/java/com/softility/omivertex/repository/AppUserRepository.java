package com.softility.omivertex.repository;

import com.softility.omivertex.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmailIgnoreCase(String email);
    List<AppUser> findAllByOrderByCreatedAtDesc();
}
