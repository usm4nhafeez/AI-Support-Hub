package com.aisupporthub.security;

import com.aisupporthub.service.ClientService;
import com.aisupporthub.exception.ResourceNotFoundException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class ApiKeyFilter extends OncePerRequestFilter {
    private final ClientService clientService;

    public ApiKeyFilter(ClientService clientService) {
        this.clientService = clientService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String clientKey = request.getHeader("X-Client-Key");
        String apiKey = request.getHeader("X-API-Key");

        if (clientKey != null && apiKey != null && request.getHeader("Authorization") == null) {
            try {
                var client = clientService.getByKey(clientKey);
                if (clientService.validApiKey(client, apiKey)) {
                    SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                            clientKey, null, List.of(new SimpleGrantedAuthority("ROLE_CLIENT"))));
                }
            } catch (ResourceNotFoundException ignored) {
                // Invalid client credentials remain unauthenticated and are rejected by Spring Security.
            }
        }
        chain.doFilter(request, response);
    }
}
