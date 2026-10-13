package com.aisupporthub.config;

import com.aisupporthub.model.entity.Agent;
import com.aisupporthub.model.entity.Client;
import com.aisupporthub.model.enums.AgentRole;
import com.aisupporthub.repository.AgentRepository;
import com.aisupporthub.repository.ClientRepository;
import com.aisupporthub.service.KnowledgeService;
import com.aisupporthub.model.dto.CreateKnowledgeRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Seeds demo data at startup. Placeholder credentials are never seeded:
 * a client or agent is only created when its real secret is supplied via env,
 * otherwise that step is skipped with a warning.
 */
@Component
public class DemoDataInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    private final ClientRepository clientRepository;
    private final AgentRepository agentRepository;
    private final PasswordEncoder passwordEncoder;
    private final KnowledgeService knowledgeService;

    @Value("${support.demo.enabled}") boolean demoEnabled;
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
        if (!demoEnabled) {
            log.info("Demo data seeding disabled (support.demo.enabled=false).");
            return;
        }

        Client client = clientRepository.findByClientKey(clientKey).orElse(null);
        if (client == null) {
            if (isPlaceholder(apiKey)) {
                log.warn("Demo client '{}' NOT seeded: DEMO_API_KEY is unset or a placeholder. "
                    + "Set a strong secret in .env to seed it; placeholder credentials are never stored.", clientKey);
            } else {
                Client c = new Client();
                c.setClientKey(clientKey);
                c.setName("Demo Client");
                c.setApiKeyHash(passwordEncoder.encode(apiKey));
                client = clientRepository.save(c);
                log.info("Demo client '{}' seeded.", clientKey);
            }
        }

        if (agentRepository.findByUsername(agentUsername).isEmpty()) {
            if (client == null) {
                log.warn("Demo agent '{}' NOT seeded: no demo client available.", agentUsername);
            } else if (isPlaceholder(agentPassword)) {
                log.warn("Demo agent '{}' NOT seeded: DEMO_AGENT_PASSWORD is unset or a placeholder. "
                    + "Set a strong secret in .env to seed it; placeholder credentials are never stored.", agentUsername);
            } else {
                Agent agent = new Agent();
                agent.setClient(client);
                agent.setUsername(agentUsername);
                agent.setPasswordHash(passwordEncoder.encode(agentPassword));
                agent.setRole(AgentRole.ADMIN);
                agentRepository.save(agent);
                log.info("Demo agent '{}' seeded.", agentUsername);
            }
        }

        if (client == null) {
            return;
        }
        ClassPathResource resource = new ClassPathResource("knowledge/support.txt");
        String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        knowledgeService.ingest(client, new CreateKnowledgeRequest(
            "support.txt", "general", "1.0", content));
    }

    private static boolean isPlaceholder(String secret) {
        return secret == null || secret.isBlank() || "change-me".equals(secret);
    }
}
