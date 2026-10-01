package com.aisupporthub.repository;

import com.aisupporthub.model.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByClientIdAndExternalCustomerId(Long clientId, String externalCustomerId);
}
