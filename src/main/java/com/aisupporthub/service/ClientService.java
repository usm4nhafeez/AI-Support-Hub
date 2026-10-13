package com.aisupporthub.service;

import com.aisupporthub.model.dto.ClientKeyRotation;
import com.aisupporthub.model.dto.CreateClientRequest;
import com.aisupporthub.model.entity.Client;
import com.aisupporthub.repository.ClientRepository;
import com.aisupporthub.exception.ConflictException;
import com.aisupporthub.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

@Service
public class ClientService {
    private static final Logger log = LoggerFactory.getLogger(ClientService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_KEY_LENGTH = 100;

    private final ClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;

    public ClientService(ClientRepository clientRepository, PasswordEncoder passwordEncoder) {
        this.clientRepository = clientRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Client getByKey(String clientKey) {
        return clientRepository.findByClientKey(clientKey)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Client with key '" + clientKey + "' was not found"));
    }

    public Client getById(Long id) {
        return clientRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Client " + id + " was not found"));
    }

    public List<Client> listAll() {
        return clientRepository.findAll(Sort.by("name"));
    }

    public Client create(CreateClientRequest request) {
        // client_key is unique in the database. A freshly generated key can still
        // collide (same project name, or a re-roll that lands on an existing key),
        // and that used to fail the whole onboarding. So the server resolves the
        // collision itself by appending a short random suffix until the key is free.
        // Callers must read the issued key back from the response.
        String base = request.clientKey().trim();
        if (base.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("clientKey must be at most " + MAX_KEY_LENGTH + " characters.");
        }
        String candidate = base;
        for (int attempt = 0; attempt < 8; attempt++) {
            if (clientRepository.findByClientKey(candidate).isEmpty()) {
                Client client = new Client();
                client.setClientKey(candidate);
                client.setName(request.name());
                client.setApiKeyHash(passwordEncoder.encode(request.apiKey()));
                try {
                    return clientRepository.save(client);
                } catch (DataIntegrityViolationException raced) {
                    // Another request claimed the key between the check and the insert.
                    log.debug("Client key race on '{}' (attempt {}), re-deriving", candidate, attempt + 1);
                }
            }
            candidate = candidate + "-" + randomSuffix();
        }
        throw new ConflictException(
            "Could not allocate a unique client key from '" + base + "'. Please generate a new one.");
    }

    private static String randomSuffix() {
        byte[] bytes = new byte[3];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * Issues a fresh API secret for an existing client without touching anything else:
     * the tenant id, the (unique) client key, the active flag and all trained knowledge
     * stay exactly as they are. The plaintext key is returned once here and stored only
     * as a BCrypt hash; the previous key stops working immediately.
     */
    public ClientKeyRotation rotateApiKey(Long id) {
        Client client = getById(id);
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        String plainApiKey = "ash_" + HexFormat.of().formatHex(bytes);
        client.setApiKeyHash(passwordEncoder.encode(plainApiKey));
        clientRepository.save(client);
        // @PreUpdate refreshes updated_at at flush time, so report the wall clock here.
        log.info("API key rotated for client id {} (key '{}')", client.getId(), client.getClientKey());
        return new ClientKeyRotation(client.getId(), client.getClientKey(), client.getName(),
            plainApiKey, LocalDateTime.now());
    }

    public boolean validApiKey(Client client, String apiKey) {
        return client.isActive() && passwordEncoder.matches(apiKey, client.getApiKeyHash());
    }
}
