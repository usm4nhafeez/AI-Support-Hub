package com.aisupporthub.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAgentRequest(
    @NotBlank @Size(min = 3, max = 80) String username,
    @NotBlank @Size(min = 8, max = 100) String password
) {}
