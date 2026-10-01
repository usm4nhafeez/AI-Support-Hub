package com.aisupporthub.service;

import com.aisupporthub.model.entity.Agent;
import com.aisupporthub.exception.InvalidCredentialsException;
import com.aisupporthub.repository.AgentRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AgentService {
    private final AgentRepository repository;
    private final PasswordEncoder passwordEncoder;

    public AgentService(AgentRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    public Agent authenticate(String username, String password) {
        Agent agent = repository.findByUsername(username)
            .orElseThrow(InvalidCredentialsException::new);
        if (!agent.isActive() || !passwordEncoder.matches(password, agent.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return agent;
    }
}
