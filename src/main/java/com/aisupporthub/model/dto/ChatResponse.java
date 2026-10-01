package com.aisupporthub.model.dto;

public record ChatResponse(
    String conversationId,
    String status,
    String response
) {}
