package com.aisupporthub.model.dto;

import java.time.LocalDateTime;

/** One support-agent login account (no secrets — the password hash never leaves the DB). */
public record AgentSummary(
    Long id,
    String username,
    String role,
    boolean active,
    LocalDateTime createdAt
) {}
