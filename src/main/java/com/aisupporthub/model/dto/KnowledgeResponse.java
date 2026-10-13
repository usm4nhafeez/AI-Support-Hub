package com.aisupporthub.model.dto;

import java.time.LocalDateTime;

public record KnowledgeResponse(
    Long id,
    String name,
    String category,
    String version,
    LocalDateTime createdAt
) {}