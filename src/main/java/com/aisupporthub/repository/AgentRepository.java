package com.aisupporthub.repository;

import com.aisupporthub.model.entity.Agent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AgentRepository extends JpaRepository<Agent, Long> {
    Optional<Agent> findByUsername(String username);

    /** All agent accounts belonging to one tenant (client), A→Z. */
    List<Agent> findByClientIdOrderByUsernameAsc(Long clientId);
}
