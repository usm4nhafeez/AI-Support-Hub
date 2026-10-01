package com.aisupporthub.service;

import com.aisupporthub.model.entity.Client;
import com.aisupporthub.model.entity.Conversation;
import com.aisupporthub.model.entity.Ticket;
import com.aisupporthub.model.enums.Priority;
import com.aisupporthub.repository.TicketRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TicketService {
    private final TicketRepository repository;

    public TicketService(TicketRepository repository) {
        this.repository = repository;
    }

    public Ticket create(Client client, Conversation conversation, String description, Priority priority) {
        Ticket ticket = new Ticket();
        ticket.setTicketKey("TCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        ticket.setClient(client);
        ticket.setConversation(conversation);
        ticket.setDescription(description);
        ticket.setPriority(priority == null ? Priority.MEDIUM : priority);
        return repository.save(ticket);
    }
}
