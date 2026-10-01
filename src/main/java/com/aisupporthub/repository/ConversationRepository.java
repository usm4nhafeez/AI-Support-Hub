package com.aisupporthub.repository;

import com.aisupporthub.model.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    Optional<Conversation> findByConversationKeyAndClientId(String conversationKey, Long clientId);
    List<Conversation> findByClientIdOrderByUpdatedAtDesc(Long clientId);
}
