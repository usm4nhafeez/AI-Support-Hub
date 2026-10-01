package com.aisupporthub.repository;

import com.aisupporthub.model.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long> {
    /**
     * Find a Client by its clientKey.
     *
     * No explicit implementation is provided — Spring Data JPA derives the query from the method name
     * (this is called "query derivation"). The method name is analysed as:
     *   find + By + ClientKey
     * which Spring translates into a query similar to:
     *   SELECT * FROM client WHERE client_key = ?
     *
     * Example usage:
     *   clientRepository.findByClientKey("bookvault");
     *
     * Under the hood the flow is roughly:
     *   Spring Data JPA -> create query from method name -> Hibernate -> JDBC -> database
     *
     * Returns an Optional containing the matching Client, or Optional.empty() if none found.
     *
     * you can explicitly provide one with @Query.
     *     @Query("SELECT c FROM Client c WHERE c.clientKey = :key")
     *     Optional<Client> findClient(@Param("key") String key);
     *
     */
    Optional<Client> findByClientKey(String clientKey);
}

/** JpaRepository already provides:
        - save(S entity)
        - findById(ID id)
        - findAll()
        - deleteById(ID id)
        - delete(S entity)
        - Count()

the actual implementation of save is roughly:
             ClientService
                 ↓
             ClientRepository
                 ↓
             JpaRepository
                 ↓
             SimpleJpaRepository
                 ↓
             EntityManager
                 ↓
             Hibernate
                 ↓
             JDBC
                 ↓
             Oracle


 */