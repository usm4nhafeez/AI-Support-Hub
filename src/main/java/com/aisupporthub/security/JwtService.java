package com.aisupporthub.security;

import com.aisupporthub.model.entity.Agent;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {
    private final SecretKey key;
    private final long expiration;

    public JwtService(@Value("${support.security.jwt-secret}") String secret,
                      @Value("${support.security.jwt-expiration-ms}") long expiration) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
    }

    public String generateToken(Agent agent) {
        Date now = new Date();
        return Jwts.builder()
            .subject(agent.getUsername())
            .claim("role", agent.getRole().name())
            .claim("clientId", agent.getClient().getId())
            .issuedAt(now)
            .expiration(new Date(now.getTime() + expiration))
            .signWith(key)
            .compact();
    }

    public String username(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject();
    }

    public boolean valid(String token) {
        try {
            username(token);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
