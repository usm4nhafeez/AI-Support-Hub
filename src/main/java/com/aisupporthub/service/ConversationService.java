package com.aisupporthub.service;

import com.aisupporthub.model.dto.ConversationSummary;
import com.aisupporthub.model.entity.Agent;
import com.aisupporthub.model.entity.Client;
import com.aisupporthub.model.entity.Conversation;
import com.aisupporthub.model.entity.Customer;
import com.aisupporthub.model.entity.Message;
import com.aisupporthub.exception.ResourceNotFoundException;
import com.aisupporthub.model.enums.ConversationStatus;
import com.aisupporthub.model.enums.MessageRole;
import com.aisupporthub.repository.AgentRepository;
import com.aisupporthub.repository.ConversationRepository;
import com.aisupporthub.repository.CustomerRepository;
import com.aisupporthub.repository.MessageRepository;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ConversationService {
    private final ConversationRepository conversationRepository;
    private final CustomerRepository customerRepository;
    private final MessageRepository messageRepository;
    private final AgentRepository agentRepository;

    public ConversationService(ConversationRepository conversationRepository,
                               CustomerRepository customerRepository,
                               MessageRepository messageRepository,
                               AgentRepository agentRepository) {
        this.conversationRepository = conversationRepository;
        this.customerRepository = customerRepository;
        this.messageRepository = messageRepository;
        this.agentRepository = agentRepository;
    }

    public Conversation getOrCreate(Client client, String customerId, String conversationId) {
        if (conversationId != null && !conversationId.isBlank()) {
            return conversationRepository.findByConversationKeyAndClientId(conversationId, client.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Conversation '" + conversationId + "' was not found for this client"));
        }

        Customer customer = customerRepository.findByClientIdAndExternalCustomerId(client.getId(), customerId)
            .orElseGet(() -> {
                Customer c = new Customer();
                c.setClient(client);
                c.setExternalCustomerId(customerId);
                return customerRepository.save(c);
            });

        Conversation conversation = new Conversation();
        conversation.setConversationKey(UUID.randomUUID().toString());
        conversation.setClient(client);
        conversation.setCustomer(customer);
        conversation.setStatus(ConversationStatus.AI_HANDLING);
        return conversationRepository.save(conversation);
    }

    public Message addMessage(Conversation conversation, MessageRole role, String content) {
        Message message = new Message();
        message.setConversation(conversation);
        message.setRole(role);
        message.setContent(content);
        return messageRepository.save(message);
    }

    public List<Message> recentMessages(Conversation conversation) {
        List<Message> messages = messageRepository.findTop20ByConversationIdOrderByCreatedAtDesc(conversation.getId());
        Collections.reverse(messages);
        return messages;
    }

    public void updateStatus(Conversation conversation, ConversationStatus status) {
        conversation.setStatus(status);
        conversationRepository.save(conversation);
    }

    public List<ConversationSummary> listByClientId(Long clientId) {
        List<Conversation> conversations =
            conversationRepository.findByClientIdOrderByUpdatedAtDesc(clientId);
        Map<Long, String> agentNames = agentNamesFor(conversations);
        return conversations.stream()
            .map(c -> toSummary(c, c.getAssignedAgentId() == null
                ? null : agentNames.get(c.getAssignedAgentId())))
            .toList();
    }

    /** First generated (or fallback) summary wins; later turns keep it. */
    public void setSummaryIfMissing(Conversation conversation, String summary) {
        if (conversation.getSummary() != null && !conversation.getSummary().isBlank()) {
            return;
        }
        String text = summary == null ? "" : summary.trim();
        if (text.isEmpty()) {
            return;
        }
        conversation.setSummary(text.length() > 1900 ? text.substring(0, 1900) : text);
        conversationRepository.save(conversation);
    }

    /**
     * Hands the conversation to {@code username} and marks it ASSIGNED (idempotent).
     * The target must be an active agent of the SAME tenant as the conversation;
     * anything else answers "not found" so other tenants' usernames stay hidden.
     */
    public ConversationSummary assign(Long conversationId, Long clientId, String username) {
        Conversation conversation = findOwned(conversationId, clientId);
        Agent agent = agentRepository.findByUsername(username)
            .filter(a -> a.isActive() && a.getClient().getId().equals(clientId))
            .orElseThrow(() -> new ResourceNotFoundException("Agent '" + username + "' was not found"));
        conversation.setAssignedAgentId(agent.getId());
        if (conversation.getStatus() != ConversationStatus.RESOLVED
            && conversation.getStatus() != ConversationStatus.CLOSED) {
            conversation.setStatus(ConversationStatus.ASSIGNED);
        }
        conversationRepository.save(conversation);
        return toSummary(conversation, agent.getUsername());
    }

    /** Puts the conversation back to the queue; the bot resumes on the next customer message. */
    public ConversationSummary release(Long conversationId, Long clientId) {
        Conversation conversation = findOwned(conversationId, clientId);
        conversation.setAssignedAgentId(null);
        if (conversation.getStatus() == ConversationStatus.ASSIGNED) {
            conversation.setStatus(ConversationStatus.OPEN);
        }
        conversationRepository.save(conversation);
        return toSummary(conversation, null);
    }

    private Conversation findOwned(Long conversationId, Long clientId) {
        Conversation conversation = conversationRepository.findById(conversationId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Conversation " + conversationId + " was not found"));
        if (!conversation.getClient().getId().equals(clientId)) {
            // Same answer as "missing" so other tenants' ids are not discoverable.
            throw new ResourceNotFoundException("Conversation " + conversationId + " was not found");
        }
        return conversation;
    }

    private Map<Long, String> agentNamesFor(List<Conversation> conversations) {
        Set<Long> ids = conversations.stream()
            .map(Conversation::getAssignedAgentId)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return agentRepository.findAllById(ids).stream()
            .collect(Collectors.toMap(Agent::getId, Agent::getUsername));
    }

    private ConversationSummary toSummary(Conversation c, String agentName) {
        return new ConversationSummary(
            c.getId(),
            c.getConversationKey(),
            c.getCustomer().getExternalCustomerId(),
            c.getStatus().name(),
            c.getPriority().name(),
            c.getAssignedAgentId(),
            agentName,
            c.getSummary(),
            c.getCreatedAt(),
            c.getUpdatedAt()
        );
    }
}
