package com.aisupporthub.controller;

import com.aisupporthub.model.dto.CreateKnowledgeRequest;
import com.aisupporthub.model.dto.KnowledgeResponse;
import com.aisupporthub.model.entity.Client;
import com.aisupporthub.model.entity.KnowledgeDocument;
import com.aisupporthub.repository.KnowledgeDocumentRepository;
import com.aisupporthub.service.ClientService;
import com.aisupporthub.service.KnowledgeService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Agent-facing knowledge management scoped to a chosen project (client),
 * so an agent can train a specific tenant without holding its API key.
 * Protected by ROLE_AGENT via the /api/v1/clients/** rule in SecurityConfig.
 */
@RestController
@RequestMapping("/api/v1/clients/{clientId}/knowledge")
public class AgentKnowledgeController {
    private final ClientService clientService;
    private final KnowledgeService knowledgeService;
    private final KnowledgeDocumentRepository documentRepository;

    public AgentKnowledgeController(ClientService clientService,
                                    KnowledgeService knowledgeService,
                                    KnowledgeDocumentRepository documentRepository) {
        this.clientService = clientService;
        this.knowledgeService = knowledgeService;
        this.documentRepository = documentRepository;
    }

    @GetMapping
    public List<KnowledgeResponse> list(@PathVariable Long clientId) {
        clientService.getById(clientId); // 404 when the project does not exist
        return documentRepository.findByClientIdOrderByCreatedAtDesc(clientId).stream()
            .map(AgentKnowledgeController::toResponse)
            .toList();
    }

    @PostMapping
    public KnowledgeResponse ingest(@PathVariable Long clientId,
                                    @Valid @RequestBody CreateKnowledgeRequest request) {
        Client client = clientService.getById(clientId);
        return toResponse(knowledgeService.ingest(client, request));
    }

    @PostMapping(value = "/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public KnowledgeResponse ingestFile(@PathVariable Long clientId,
                                        @RequestPart("file") MultipartFile file,
                                        @RequestParam(required = false) String name,
                                        @RequestParam(required = false) String category,
                                        @RequestParam(required = false) String version) {
        Client client = clientService.getById(clientId);
        return toResponse(knowledgeService.ingestFile(client, name, category, version, file));
    }

    private static KnowledgeResponse toResponse(KnowledgeDocument document) {
        return new KnowledgeResponse(document.getId(), document.getName(), document.getCategory(),
            document.getVersion(), document.getCreatedAt());
    }
}
