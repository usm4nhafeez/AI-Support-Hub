package com.aisupporthub.model.dto;

import java.time.LocalDateTime;

public record ConversationSummary(
        Long id,
        String conversationKey,
        String customerId,
        String status,
        String priority,
        Long assignedAgentId,
        String assignedAgentName,
        String summary,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
