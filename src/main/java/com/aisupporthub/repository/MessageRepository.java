package com.aisupporthub.repository;

import com.aisupporthub.model.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findTop20ByConversationIdOrderByCreatedAtDesc(Long conversationId);
}
