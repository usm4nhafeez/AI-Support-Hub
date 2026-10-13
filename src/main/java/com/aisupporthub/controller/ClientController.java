package com.aisupporthub.controller;

import com.aisupporthub.model.dto.AgentRemoval;
import com.aisupporthub.model.dto.AgentSummary;
import com.aisupporthub.model.dto.ClientKeyRotation;
import com.aisupporthub.model.dto.CreateAgentRequest;
import com.aisupporthub.model.dto.CreateClientRequest;
import com.aisupporthub.model.dto.ClientResponse;
import com.aisupporthub.model.entity.Client;
import com.aisupporthub.security.AgentPrincipal;
import com.aisupporthub.service.AgentService;
import com.aisupporthub.service.ClientService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/clients")
public class ClientController {
    private final ClientService service;
    private final AgentService agentService;

    public ClientController(ClientService service, AgentService agentService) {
        this.service = service;
        this.agentService = agentService;
    }

    @PostMapping
    public ClientResponse create(@Valid @RequestBody CreateClientRequest request) {
        return toResponse(service.create(request));
    }

    @GetMapping
    public List<ClientResponse> list() {
        return service.listAll().stream().map(ClientController::toResponseStatic).toList();
    }

    /**
     * Replaces the client's API secret in place: same tenant id, same client key,
     * same trained knowledge. The new plaintext key appears only in this response.
     */
    @PostMapping("/{id}/rotate-api-key")
    public ClientKeyRotation rotateApiKey(@PathVariable Long id) {
        return service.rotateApiKey(id);
    }

    /** Agent logins that belong to this project. */
    @GetMapping("/{id}/agents")
    public List<AgentSummary> listAgents(@PathVariable Long id) {
        return agentService.listForClient(id);
    }

    /**
     * Add a support-agent login for this project. Usernames are globally unique; a
     * taken name is auto-resolved with a short suffix — the response username is the
     * exact one to sign in with. The password is stored only as a BCrypt hash.
     */
    @PostMapping("/{id}/agents")
    public AgentSummary createAgent(@PathVariable Long id,
                                    @Valid @RequestBody CreateAgentRequest request) {
        return agentService.create(id, request);
    }

    /**
     * Remove a support-agent login. Its tickets return to the queue and the username
     * becomes free again; the project, its API keys and its trained knowledge stay.
     * You cannot remove the account you are signed in with, or a project's last admin.
     */
    @DeleteMapping("/{id}/agents/{agentId}")
    public AgentRemoval deleteAgent(@AuthenticationPrincipal AgentPrincipal principal,
                                    @PathVariable Long id,
                                    @PathVariable Long agentId) {
        return agentService.delete(id, agentId, principal.username());
    }

    private static ClientResponse toResponseStatic(Client client) {
        return new ClientResponse(client.getId(), client.getClientKey(), client.getName(),
            client.isActive(), client.getCreatedAt(), client.getUpdatedAt());
    }

    private ClientResponse toResponse(Client client) {
        return toResponseStatic(client);
    }
}
