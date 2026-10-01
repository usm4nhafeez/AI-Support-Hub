package com.aisupporthub.config;

import com.aisupporthub.security.ApiKeyFilter;
import com.aisupporthub.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            ApiKeyFilter apiKeyFilter,
                                            JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
       //csrf - Cross-site request forgery
        return http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info", "/api/v1/agent/auth/login").permitAll()
                .requestMatchers("/api/v1/support/**", "/api/v1/knowledge/**").hasRole("CLIENT")
                .requestMatchers("/api/v1/agent/**").hasRole("AGENT")
                .requestMatchers("/api/v1/clients/**").hasRole("AGENT")
                .anyRequest().authenticated())
            .addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
