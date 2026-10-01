package com.aisupporthub.repository;

import com.aisupporthub.model.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    List<Ticket> findByClientIdOrderByCreatedAtDesc(Long clientId);
}
