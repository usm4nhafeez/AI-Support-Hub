package com.aisupporthub.controller;

import com.aisupporthub.model.dto.AgentLoginRequest;
import com.aisupporthub.model.dto.AgentLoginResponse;
import com.aisupporthub.model.entity.Agent;
import com.aisupporthub.security.JwtService;
import com.aisupporthub.service.AgentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/agent/auth")
public class AgentAuthController {
    private final AgentService agentService;
    private final JwtService jwtService;

    public AgentAuthController(AgentService agentService, JwtService jwtService) {
        this.agentService = agentService;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public AgentLoginResponse login(@Valid @RequestBody AgentLoginRequest request) {
        Agent agent = agentService.authenticate(request.username(), request.password());
        return new AgentLoginResponse(jwtService.generateToken(agent));
    }
}
