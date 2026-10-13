package com.aisupporthub.model.dto;

/**
 * Optional target for the assign endpoint. When {@code username} is null/blank the
 * conversation is assigned to the calling agent ("Assign to me").
 */
public record AssignAgentRequest(String username) {}
