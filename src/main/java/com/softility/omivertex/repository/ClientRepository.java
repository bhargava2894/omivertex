package com.softility.omivertex.repository;

import com.softility.omivertex.domain.Client;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<Client, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByClientIdIgnoreCase(String clientId);

    java.util.Optional<Client> findByNameIgnoreCase(String name);
}
