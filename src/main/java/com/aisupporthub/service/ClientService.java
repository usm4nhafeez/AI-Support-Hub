package com.aisupporthub.service;

import com.aisupporthub.model.dto.CreateClientRequest;
import com.aisupporthub.model.entity.Client;
import com.aisupporthub.repository.ClientRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
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
            .orElseThrow(() -> new IllegalArgumentException("Client not found"));
    }

    public Client create(CreateClientRequest request) {
        Client client = new Client();
        client.setClientKey(request.clientKey());
        client.setName(request.name());
        client.setApiKeyHash(passwordEncoder.encode(request.apiKey()));
        return clientRepository.save(client);
    }

    public boolean validApiKey(Client client, String apiKey) {
        return client.isActive() && passwordEncoder.matches(apiKey, client.getApiKeyHash());
    }
}
