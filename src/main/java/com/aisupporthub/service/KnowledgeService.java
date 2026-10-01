package com.aisupporthub.service;

import com.aisupporthub.model.dto.CreateKnowledgeRequest;
import com.aisupporthub.model.entity.Client;
import com.aisupporthub.model.entity.KnowledgeDocument;
import com.aisupporthub.repository.KnowledgeDocumentRepository;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class KnowledgeService {
    private final VectorStore vectorStore;
    private final KnowledgeDocumentRepository repository;

    public KnowledgeService(VectorStore vectorStore, KnowledgeDocumentRepository repository) {
        this.vectorStore = vectorStore;
        this.repository = repository;
    }

    public KnowledgeDocument ingest(Client client, CreateKnowledgeRequest request) {
        KnowledgeDocument knowledge = new KnowledgeDocument();
        knowledge.setClient(client);
        knowledge.setName(request.name());
        knowledge.setCategory(request.category());
        knowledge.setVersion(request.version());
        knowledge = repository.save(knowledge);

        Document document = new Document(
            request.content(),
            Map.of(
                "clientKey", client.getClientKey(),
                "documentId", knowledge.getId().toString(),
                "documentName", request.name(),
                "category", request.category() == null ? "" : request.category()
            )
        );

        TokenTextSplitter splitter = TokenTextSplitter.builder()
            .withChunkSize(800)
            .withMinChunkSizeChars(200)
            .build();

        List<Document> chunks = splitter.apply(List.of(document));
        vectorStore.add(chunks);
        return knowledge;
    }
}
