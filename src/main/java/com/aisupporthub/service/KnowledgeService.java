package com.aisupporthub.service;

import com.aisupporthub.model.dto.CreateKnowledgeRequest;
import com.aisupporthub.model.entity.Client;
import com.aisupporthub.model.entity.KnowledgeDocument;
import com.aisupporthub.repository.KnowledgeDocumentRepository;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Locale;

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

        Document document = new Document(request.content());
        addMetadata(document, client, knowledge, request.name(), request.category());
        addToVectorStore(List.of(document));
        return knowledge;
    }

    public KnowledgeDocument ingestFile(Client client, String name, String category, String version,
                                        MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Knowledge file must not be empty.");
        }

        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extension = extension(filename);
        DocumentReader reader = switch (extension) {
            case "pdf" -> new PagePdfDocumentReader(file.getResource());
            case "md", "markdown" -> new MarkdownDocumentReader(file.getResource(),
                MarkdownDocumentReaderConfig.defaultConfig());
            default -> throw new IllegalArgumentException(
                "Only PDF and Markdown files are supported.");
        };

        KnowledgeDocument knowledge = new KnowledgeDocument();
        knowledge.setClient(client);
        knowledge.setName(name == null || name.isBlank() ? filename : name.trim());
        knowledge.setCategory(category);
        knowledge.setVersion(version);
        KnowledgeDocument savedKnowledge = repository.save(knowledge);

        List<Document> documents = reader.get();
        documents.forEach(document -> addMetadata(document, client, savedKnowledge, savedKnowledge.getName(), category));
        addToVectorStore(documents);
        return savedKnowledge;
    }

    private void addMetadata(Document document, Client client, KnowledgeDocument knowledge,
                              String name, String category) {
        document.getMetadata().putAll(Map.of(
            "clientKey", client.getClientKey(),
            "documentId", knowledge.getId().toString(),
            "documentName", name,
            "category", category == null ? "" : category
        ));
    }

    private void addToVectorStore(List<Document> documents) {
        TokenTextSplitter splitter = TokenTextSplitter.builder()
            .withChunkSize(800)
            .withMinChunkSizeChars(200)
            .build();

        List<Document> chunks = splitter.apply(documents);
        vectorStore.add(chunks);
    }

    private String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
