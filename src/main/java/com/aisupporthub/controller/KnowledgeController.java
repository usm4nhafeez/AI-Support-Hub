package com.aisupporthub.controller;

import com.aisupporthub.model.dto.CreateKnowledgeRequest;
import com.aisupporthub.model.entity.Client;
import com.aisupporthub.model.entity.KnowledgeDocument;
import com.aisupporthub.service.ClientService;
import com.aisupporthub.service.KnowledgeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeController {
    private final ClientService clientService;
    private final KnowledgeService knowledgeService;

    public KnowledgeController(ClientService clientService, KnowledgeService knowledgeService) {
        this.clientService = clientService;
        this.knowledgeService = knowledgeService;
    }

    @PostMapping
    public KnowledgeDocument ingest(@RequestHeader("X-Client-Key") String clientKey,
                                    @Valid @RequestBody CreateKnowledgeRequest request) {
        Client client = clientService.getByKey(clientKey);
        return knowledgeService.ingest(client, request);
    }
}
