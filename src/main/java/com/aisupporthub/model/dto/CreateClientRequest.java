package com.aisupporthub.model.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateClientRequest(
    @NotBlank String clientKey,
    @NotBlank String name,
    @NotBlank String apiKey
) {}
