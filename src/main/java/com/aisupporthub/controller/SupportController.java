package com.aisupporthub.controller;

import com.aisupporthub.ai.SupportAiService;
import com.aisupporthub.model.dto.ChatRequest;
import com.aisupporthub.model.dto.ChatResponse;
import com.aisupporthub.model.entity.Client;
import com.aisupporthub.model.entity.Conversation;
import com.aisupporthub.model.enums.ConversationStatus;
import com.aisupporthub.model.enums.MessageRole;
import com.aisupporthub.service.ClientService;
import com.aisupporthub.service.ConversationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/support")
public class SupportController {
    private final ClientService clientService;
    private final ConversationService conversationService;
    private final SupportAiService supportAiService;

    public SupportController(ClientService clientService,
                             ConversationService conversationService,
                             SupportAiService supportAiService) {
        this.clientService = clientService;
        this.conversationService = conversationService;
        this.supportAiService = supportAiService;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestHeader("X-Client-Key") String clientKey,
                             @Valid @RequestBody ChatRequest request) {
        Client client = clientService.getByKey(clientKey);
        Conversation conversation = conversationService.getOrCreate(client, request.customerId(), request.conversationId());
        conversationService.addMessage(conversation, MessageRole.CUSTOMER, request.message());

        String answer = supportAiService.answer(client, conversation, request.message());
        conversationService.addMessage(conversation, MessageRole.AI, answer);
        conversationService.updateStatus(conversation, ConversationStatus.AI_HANDLING);

        return new ChatResponse(conversation.getConversationKey(), conversation.getStatus().name(), answer);
    }
}
