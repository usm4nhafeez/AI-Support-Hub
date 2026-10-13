package com.aisupporthub.model.dto;

/**
 * Result of removing a support-agent login. {@code ticketsReleased} is how many
 * conversations were assigned to that agent and went back to the queue.
 */
public record AgentRemoval(
    Long id,
    String username,
    int ticketsReleased
) {}
