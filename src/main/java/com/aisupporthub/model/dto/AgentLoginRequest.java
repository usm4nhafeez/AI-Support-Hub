package com.aisupporthub.model.dto;

import jakarta.validation.constraints.NotBlank;

public record AgentLoginRequest(
    @NotBlank String username,
    @NotBlank String password
) {}
