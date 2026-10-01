package com.aisupporthub.service;

import com.aisupporthub.model.dto.CreateClientRequest;
import com.aisupporthub.model.entity.Client;
import com.aisupporthub.repository.ClientRepository;
import com.aisupporthub.exception.ConflictException;
import com.aisupporthub.exception.ResourceNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class ClientService {
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

    public Client create(CreateClientRequest request) {
        if (clientRepository.findByClientKey(request.clientKey()).isPresent()) {
            throw new ConflictException(
                "Client key '" + request.clientKey() + "' already exists. Use a unique clientKey.");
        }

        Client client = new Client();
        client.setClientKey(request.clientKey());
        client.setName(request.name());
        client.setApiKeyHash(passwordEncoder.encode(request.apiKey()));
        try {
            return clientRepository.save(client);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException(
                "Client key '" + request.clientKey() + "' already exists. Use a unique clientKey.");
        }
    }

    public boolean validApiKey(Client client, String apiKey) {
        return client.isActive() && passwordEncoder.matches(apiKey, client.getApiKeyHash());
    }
}
