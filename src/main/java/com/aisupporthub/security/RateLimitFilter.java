package com.aisupporthub.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory fixed-window rate limiter for the two endpoints an attacker can hit
 * without valid credentials or that cost money per call:
 * - agent login  : 10 requests/min per IP        (brute-force protection)
 * - support chat : 30 requests/min per client/IP (Gemini cost protection)
 *
 * Instantiated directly in SecurityConfig (not a @Component) so it runs exactly
 * once inside the Spring Security filter chain and is never auto-registered.
 */
public class RateLimitFilter extends OncePerRequestFilter {
    private static final String LOGIN_PATH = "/api/v1/agent/auth/login";
    private static final String CHAT_PATH = "/api/v1/support/chat";
    private static final int LOGIN_LIMIT = 10;
    private static final int CHAT_LIMIT = 30;
    private static final long WINDOW_MS = 60_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong requestCounter = new AtomicLong();

    private static final class Window {
        final long start;
        int count;
        Window(long start) { this.start = start; }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String path = request.getRequestURI();
        long now = System.currentTimeMillis();

        sweepExpired(now);

        String key;
        int limit;
        if (LOGIN_PATH.equals(path)) {
            key = "login:" + request.getRemoteAddr();
            limit = LOGIN_LIMIT;
        } else if (CHAT_PATH.equals(path)) {
            String clientKey = request.getHeader("X-Client-Key");
            key = "chat:" + (clientKey != null && !clientKey.isBlank() ? clientKey : request.getRemoteAddr());
            limit = CHAT_LIMIT;
        } else {
            chain.doFilter(request, response);
            return;
        }

        Window window = windows.compute(key, (k, existing) ->
            existing == null || now - existing.start > WINDOW_MS ? new Window(now) : existing);

        boolean allowed;
        synchronized (window) {
            allowed = window.count < limit;
            if (allowed) window.count++;
        }

        if (!allowed) {
            long retryAfter = Math.max(1, (WINDOW_MS - (now - window.start)) / 1000 + 1);
            respondTooManyRequests(response, request, retryAfter);
            return;
        }
        chain.doFilter(request, response);
    }

    private void sweepExpired(long now) {
        // Periodic cleanup so the map cannot grow unbounded from many distinct keys.
        if (requestCounter.incrementAndGet() % 512 == 0) {
            windows.entrySet().removeIf(e -> now - e.getValue().start > 2 * WINDOW_MS);
        }
    }

    private void respondTooManyRequests(HttpServletResponse response, HttpServletRequest request, long retryAfterSeconds)
        throws IOException {
        response.setStatus(429);
        response.setContentType("application/json");
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        String body = "{\"timestamp\":\"" + Instant.now() + "\","
            + "\"status\":429,"
            + "\"error\":\"Too Many Requests\","
            + "\"code\":\"RATE_LIMITED\","
            + "\"message\":\"Too many requests. Please retry in " + retryAfterSeconds + " seconds.\","
            + "\"path\":\"" + request.getRequestURI() + "\"}";
        response.getWriter().write(body);
    }
}
