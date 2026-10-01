package com.aisupporthub.service;

import com.aisupporthub.model.entity.AuditEvent;
import com.aisupporthub.model.entity.Client;
import com.aisupporthub.repository.AuditEventRepository;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final AuditEventRepository repository;

    public AuditService(AuditEventRepository repository) {
        this.repository = repository;
    }

    public void log(Client client, Long conversationId, String actorType, String eventType, String metadata) {
        AuditEvent event = new AuditEvent();
        event.setClient(client);
        event.setConversationId(conversationId);
        event.setActorType(actorType);
        event.setEventType(eventType);
        event.setMetadata(metadata);
        repository.save(event);
    }
}
