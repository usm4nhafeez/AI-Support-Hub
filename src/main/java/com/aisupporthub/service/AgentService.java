package com.aisupporthub.service;

import com.aisupporthub.exception.ConflictException;
import com.aisupporthub.exception.InvalidCredentialsException;
import com.aisupporthub.exception.ResourceNotFoundException;
import com.aisupporthub.model.dto.AgentRemoval;
import com.aisupporthub.model.dto.AgentSummary;
import com.aisupporthub.model.dto.CreateAgentRequest;
import com.aisupporthub.model.entity.Agent;
import com.aisupporthub.model.entity.Client;
import com.aisupporthub.model.entity.Conversation;
import com.aisupporthub.model.enums.AgentRole;
import com.aisupporthub.model.enums.ConversationStatus;
import com.aisupporthub.repository.AgentRepository;
import com.aisupporthub.repository.ClientRepository;
import com.aisupporthub.repository.ConversationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;

@Service
public class AgentService {
    private static final Logger log = LoggerFactory.getLogger(AgentService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AgentRepository repository;
    private final ClientRepository clientRepository;
    private final ConversationRepository conversationRepository;
    private final PasswordEncoder passwordEncoder;

    public AgentService(AgentRepository repository, ClientRepository clientRepository,
                        ConversationRepository conversationRepository,
                        PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.clientRepository = clientRepository;
        this.conversationRepository = conversationRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Agent authenticate(String username, String password) {
        Agent agent = repository.findByUsername(username)
            .orElseThrow(InvalidCredentialsException::new);
        if (!agent.isActive() || !passwordEncoder.matches(password, agent.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return agent;
    }

    public List<AgentSummary> listForClient(Long clientId) {
        return repository.findByClientIdOrderByUsernameAsc(clientId).stream()
            .map(AgentService::toSummary)
            .toList();
    }

    private static AgentSummary toSummary(Agent a) {
        return new AgentSummary(a.getId(), a.getUsername(), a.getRole().name(),
            a.isActive(), a.getCreatedAt());
    }

    /**
     * Creates a login account for one tenant. Usernames are globally unique (login
     * resolves by username alone), so — same policy as client keys — a taken name is
     * auto-resolved with a short random suffix instead of failing. The response's
     * username is authoritative: that is the exact string the person must log in with.
     */
    public AgentSummary create(Long clientId, CreateAgentRequest request) {
        Client client = clientRepository.findById(clientId)
            .orElseThrow(() -> new ResourceNotFoundException("Client " + clientId + " was not found"));
        String base = request.username().trim();
        String candidate = base;
        for (int attempt = 0; attempt < 8; attempt++) {
            if (repository.findByUsername(candidate).isEmpty()) {
                Agent agent = new Agent();
                agent.setClient(client);
                agent.setUsername(candidate);
                agent.setPasswordHash(passwordEncoder.encode(request.password()));
                agent.setRole(AgentRole.AGENT);
                agent.setActive(true);
                try {
                    Agent saved = repository.save(agent);
                    log.info("Agent '{}' created for client id {}", saved.getUsername(), clientId);
                    return toSummary(saved);
                } catch (DataIntegrityViolationException raced) {
                    log.debug("Username race on '{}' (attempt {}), re-deriving", candidate, attempt + 1);
                }
            }
            candidate = base + "-" + randomSuffix();
        }
        throw new ConflictException(
            "Could not allocate a unique username from '" + base + "'. Please choose another.");
    }

    private static String randomSuffix() {
        byte[] bytes = new byte[2];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * Removes one agent login from the project. The lookup is tenant-scoped, so an id
     * belonging to another project answers "not found" — the same non-disclosure used
     * for conversations and assignment. Two accounts are protected: the one you are
     * signed in with (a self-lockout is never what was meant) and the project's last
     * active admin (that would leave nobody who can manage it). Any ticket still held
     * by the removed agent goes back to the queue, and the username becomes free to
     * reuse. The project itself, its API keys and its trained knowledge are untouched.
     */
    @Transactional
    public AgentRemoval delete(Long clientId, Long agentId, String actingUsername) {
        Agent agent = repository.findById(agentId)
            .filter(a -> a.getClient().getId().equals(clientId))
            .orElseThrow(() -> new ResourceNotFoundException(
                "Agent " + agentId + " was not found on this project"));
        String username = agent.getUsername();
        if (username.equals(actingUsername)) {
            throw new ConflictException("@" + username + " is the account you are signed in with. "
                + "Sign in as a teammate first.");
        }
        if (agent.isActive() && agent.getRole() == AgentRole.ADMIN && activeAdmins(clientId) <= 1) {
            throw new ConflictException("@" + username + " is the only active admin on this project. "
                + "Add another admin before removing this one.");
        }
        int released = releaseTickets(agentId);
        repository.delete(agent);
        log.info("Agent '{}' (id {}) removed from client id {}; {} ticket(s) released",
            username, agentId, clientId, released);
        return new AgentRemoval(agentId, username, released);
    }

    private long activeAdmins(Long clientId) {
        return repository.findByClientIdOrderByUsernameAsc(clientId).stream()
            .filter(a -> a.isActive() && a.getRole() == AgentRole.ADMIN)
            .count();
    }

    /** Clears the agent off their conversations; an ASSIGNED ticket returns to OPEN. */
    private int releaseTickets(Long agentId) {
        List<Conversation> held = conversationRepository.findByAssignedAgentId(agentId);
        for (Conversation conversation : held) {
            conversation.setAssignedAgentId(null);
            if (conversation.getStatus() == ConversationStatus.ASSIGNED) {
                conversation.setStatus(ConversationStatus.OPEN);
            }
            conversationRepository.save(conversation);
        }
        return held.size();
    }
}
