package com.aisupporthub.service;

import com.aisupporthub.model.entity.Client;
import com.aisupporthub.model.entity.Conversation;
import com.aisupporthub.model.entity.Customer;
import com.aisupporthub.model.entity.Message;
import com.aisupporthub.model.enums.ConversationStatus;
import com.aisupporthub.model.enums.MessageRole;
import com.aisupporthub.repository.ConversationRepository;
import com.aisupporthub.repository.CustomerRepository;
import com.aisupporthub.repository.MessageRepository;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class ConversationService {
    private final ConversationRepository conversationRepository;
    private final CustomerRepository customerRepository;
    private final MessageRepository messageRepository;

    public ConversationService(ConversationRepository conversationRepository,
                               CustomerRepository customerRepository,
                               MessageRepository messageRepository) {
        this.conversationRepository = conversationRepository;
        this.customerRepository = customerRepository;
        this.messageRepository = messageRepository;
    }

    public Conversation getOrCreate(Client client, String customerId, String conversationId) {
        if (conversationId != null && !conversationId.isBlank()) {
            return conversationRepository.findByConversationKeyAndClientId(conversationId, client.getId())
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
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

    public List<Conversation> list(Client client) {
        return conversationRepository.findByClientIdOrderByUpdatedAtDesc(client.getId());
    }
}
