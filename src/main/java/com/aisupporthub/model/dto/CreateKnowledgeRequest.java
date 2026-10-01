package com.aisupporthub.model.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateKnowledgeRequest(
    @NotBlank String name,
    String category,
    String version,
    @NotBlank String content
) {}
