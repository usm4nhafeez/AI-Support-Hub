package com.aisupporthub.config;

import com.aisupporthub.model.entity.Agent;
import com.aisupporthub.model.entity.Client;
import com.aisupporthub.model.enums.AgentRole;
import com.aisupporthub.repository.AgentRepository;
import com.aisupporthub.repository.ClientRepository;
import com.aisupporthub.service.KnowledgeService;
import com.aisupporthub.model.dto.CreateKnowledgeRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class DemoDataInitializer implements ApplicationRunner {
    private final ClientRepository clientRepository;
    private final AgentRepository agentRepository;
    private final PasswordEncoder passwordEncoder;
    private final KnowledgeService knowledgeService;

    @Value("${support.demo.client-key}") String clientKey;
    @Value("${support.demo.api-key}") String apiKey;
    @Value("${support.demo.agent-username}") String agentUsername;
    @Value("${support.demo.agent-password}") String agentPassword;

    public DemoDataInitializer(ClientRepository clientRepository,
                               AgentRepository agentRepository,
                               PasswordEncoder passwordEncoder,
                               KnowledgeService knowledgeService) {
        this.clientRepository = clientRepository;
        this.agentRepository = agentRepository;
        this.passwordEncoder = passwordEncoder;
        this.knowledgeService = knowledgeService;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Client client = clientRepository.findByClientKey(clientKey).orElseGet(() -> {
            Client c = new Client();
            c.setClientKey(clientKey);
            c.setName("BookVault Demo");
            c.setApiKeyHash(passwordEncoder.encode(apiKey));
            return clientRepository.save(c);
        });

        if (agentRepository.findByUsername(agentUsername).isEmpty()) {
            Agent agent = new Agent();
            agent.setClient(client);
            agent.setUsername(agentUsername);
            agent.setPasswordHash(passwordEncoder.encode(agentPassword));
            agent.setRole(AgentRole.ADMIN);
            agentRepository.save(agent);
        }

        if (knowledgeService != null && knowledgeService.getClass() != null) {
            ClassPathResource resource = new ClassPathResource("knowledge/support.txt");
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            knowledgeService.ingest(client, new CreateKnowledgeRequest(
                "support.txt", "general", "1.0", content));
        }
    }
}
