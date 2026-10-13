package com.aisupporthub.model.dto;

import java.time.LocalDateTime;

public record ClientResponse(
    Long id,
    String clientKey,
    String name,
    boolean active,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}