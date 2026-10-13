package com.aisupporthub.model.dto;

import java.time.LocalDateTime;

/**
 * Result of an API key rotation. The plaintext apiKey is returned exactly
 * once here — the hub stores only its hash.
 */
public record ClientKeyRotation(
    Long id,
    String clientKey,
    String name,
    String apiKey,
    LocalDateTime rotatedAt
) {}
