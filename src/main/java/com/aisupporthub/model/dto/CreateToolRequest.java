package com.aisupporthub.model.dto;

import com.aisupporthub.model.enums.ToolRiskLevel;
import jakarta.validation.constraints.NotBlank;

public record CreateToolRequest(
    @NotBlank String name,
    String description,
    @NotBlank String endpointUrl,
    ToolRiskLevel riskLevel
) {}
