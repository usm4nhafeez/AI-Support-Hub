package com.aisupporthub.service;

import com.aisupporthub.model.dto.CreateToolRequest;
import com.aisupporthub.model.entity.Client;
import com.aisupporthub.model.entity.ToolDefinition;
import com.aisupporthub.model.entity.ToolExecution;
import com.aisupporthub.model.enums.ToolRiskLevel;
import com.aisupporthub.repository.ToolDefinitionRepository;
import com.aisupporthub.repository.ToolExecutionRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class ToolService {
    private final ToolDefinitionRepository toolRepository;
    private final ToolExecutionRepository executionRepository;

    public ToolService(ToolDefinitionRepository toolRepository, ToolExecutionRepository executionRepository) {
        this.toolRepository = toolRepository;
        this.executionRepository = executionRepository;
    }

    public ToolDefinition create(Client client, CreateToolRequest request) {
        ToolDefinition tool = new ToolDefinition();
        tool.setClient(client);
        tool.setName(request.name());
        tool.setDescription(request.description());
        tool.setEndpointUrl(request.endpointUrl());
        tool.setRiskLevel(request.riskLevel() == null ? ToolRiskLevel.READ_ONLY : request.riskLevel());
        return toolRepository.save(tool);
    }

    public String execute(ToolDefinition tool, String payload) {
        if (tool.getRiskLevel() == ToolRiskLevel.HIGH_RISK_ACTION) {
            throw new IllegalStateException("High-risk tools require explicit approval");
        }

        ToolExecution execution = new ToolExecution();
        execution.setClient(tool.getClient());
        execution.setTool(tool);
        execution.setRequestPayload(payload);

        try {
            String response = RestClient.create().post()
                .uri(tool.getEndpointUrl())
                .body(payload)
                .retrieve()
                .body(String.class);

            execution.setResponsePayload(response);
            execution.setSuccess(true);
            executionRepository.save(execution);
            return response;
        } catch (Exception ex) {
            execution.setResponsePayload(ex.getMessage());
            execution.setSuccess(false);
            executionRepository.save(execution);
            throw new IllegalStateException("Tool execution failed");
        }
    }
}
