package com.aisupporthub.model.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
    @NotBlank String customerId,
    String conversationId,
    @NotBlank String message
) {}
