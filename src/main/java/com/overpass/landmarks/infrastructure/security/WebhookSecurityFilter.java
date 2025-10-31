package com.overpass.landmarks.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Security filter for webhook endpoint authentication.
 * Validates Bearer token from Authorization header.
 */
@Component
public class WebhookSecurityFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(WebhookSecurityFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final String webhookSecret;

    public WebhookSecurityFilter(@Value("${app.webhook.secret}") String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        
        // Only apply to webhook endpoint
        if (!request.getRequestURI().equals("/webhook")) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            logger.warn("Missing or invalid Authorization header for webhook request");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"UNAUTHORIZED\",\"message\":\"Missing or invalid Authorization header\"}");
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        
        if (!webhookSecret.equals(token)) {
            logger.warn("Invalid webhook token provided");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"UNAUTHORIZED\",\"message\":\"Invalid token\"}");
            return;
        }

        logger.debug("Webhook request authenticated successfully");
        filterChain.doFilter(request, response);
    }
}

