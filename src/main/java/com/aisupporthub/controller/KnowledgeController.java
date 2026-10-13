package com.aisupporthub.controller;

import com.aisupporthub.model.dto.CreateKnowledgeRequest;
import com.aisupporthub.model.dto.KnowledgeResponse;
import com.aisupporthub.model.entity.Client;
import com.aisupporthub.model.entity.KnowledgeDocument;
import com.aisupporthub.service.ClientService;
import com.aisupporthub.service.KnowledgeService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
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
    public KnowledgeResponse ingest(@RequestHeader("X-Client-Key") String clientKey,
                                    @Valid @RequestBody CreateKnowledgeRequest request) {
        Client client = clientService.getByKey(clientKey);
        return toResponse(knowledgeService.ingest(client, request));
    }

    private KnowledgeResponse toResponse(KnowledgeDocument document) {
        return new KnowledgeResponse(document.getId(), document.getName(), document.getCategory(),
            document.getVersion(), document.getCreatedAt());
    }

    @PostMapping(value = "/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public KnowledgeResponse ingestFile(@RequestHeader("X-Client-Key") String clientKey,
                                        @RequestPart("file") MultipartFile file,
                                        @RequestParam(required = false) String name,
                                        @RequestParam(required = false) String category,
                                        @RequestParam(required = false) String version) {
        Client client = clientService.getByKey(clientKey);
        return toResponse(knowledgeService.ingestFile(client, name, category, version, file));
    }
}
