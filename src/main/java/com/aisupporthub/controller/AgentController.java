package com.aisupporthub.controller;

import com.aisupporthub.model.dto.AgentSummary;
import com.aisupporthub.model.dto.AssignAgentRequest;
import com.aisupporthub.model.dto.ConversationSummary;
import com.aisupporthub.security.AgentPrincipal;
import com.aisupporthub.service.AgentService;
import com.aisupporthub.service.ConversationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agent")
public class AgentController {
    private final ConversationService conversationService;
    private final AgentService agentService;

    public AgentController(ConversationService conversationService, AgentService agentService) {
        this.conversationService = conversationService;
        this.agentService = agentService;
    }

    @GetMapping("/conversations")
    public List<ConversationSummary> conversations(@AuthenticationPrincipal AgentPrincipal principal) {
        return conversationService.listByClientId(principal.clientId());
    }

    /** The signed-in agent's own teammates — feeds the conversation assign dropdown. */
    @GetMapping("/team")
    public List<AgentSummary> team(@AuthenticationPrincipal AgentPrincipal principal) {
        return agentService.listForClient(principal.clientId());
    }

    /**
     * Assign this conversation to a teammate. Body optional: omit it (or send a blank
     * username) to take the ticket onto your own queue. Target must be an active agent
     * of your tenant.
     */
    @PostMapping("/conversations/{id}/assign")
    public ConversationSummary assign(@AuthenticationPrincipal AgentPrincipal principal,
                                      @PathVariable Long id,
                                      @RequestBody(required = false) AssignAgentRequest request) {
        String target = (request == null || request.username() == null || request.username().isBlank())
            ? principal.username() : request.username().trim();
        return conversationService.assign(id, principal.clientId(), target);
    }

    /** Give the conversation back to the queue / AI handling. */
    @DeleteMapping("/conversations/{id}/assign")
    public ConversationSummary release(@AuthenticationPrincipal AgentPrincipal principal,
                                       @PathVariable Long id) {
        return conversationService.release(id, principal.clientId());
    }
}
