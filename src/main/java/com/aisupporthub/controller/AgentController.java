package com.aisupporthub.controller;

import com.aisupporthub.model.entity.Client;
import com.aisupporthub.model.entity.Conversation;
import com.aisupporthub.service.ClientService;
import com.aisupporthub.service.ConversationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agent")
public class AgentController {
    private final ClientService clientService;
    private final ConversationService conversationService;

    public AgentController(ClientService clientService, ConversationService conversationService) {
        this.clientService = clientService;
        this.conversationService = conversationService;
    }

    @GetMapping("/conversations")
    public List<Conversation> conversations(@RequestHeader("X-Client-Key") String clientKey) {
        Client client = clientService.getByKey(clientKey);
        return conversationService.list(client);
    }
}
