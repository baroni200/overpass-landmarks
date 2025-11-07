package com.overpass.landmarks.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Security filter for webhook endpoint authentication.
 * Validates Bearer token from Authorization header.
 * 
 * Uses modern Spring Boot patterns with proper JSON error responses.
 */
@Component
public class WebhookSecurityFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(WebhookSecurityFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final String webhookSecret;
    private final ObjectMapper objectMapper;

    public WebhookSecurityFilter(
        @Value("${app.webhook.secret}") String webhookSecret,
        ObjectMapper objectMapper
    ) {
        this.webhookSecret = webhookSecret;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        
        // Only apply to POST /webhook endpoint (GET requests don't need auth)
        String requestUri = request.getRequestURI();
        String method = request.getMethod();
        
        if (!requestUri.equals("/webhook") || !method.equals("POST")) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            logger.warn("Missing or invalid Authorization header for webhook request");
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, 
                "UNAUTHORIZED", "Missing or invalid Authorization header");
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        
        // Use constant-time comparison to prevent timing attacks
        if (!constantTimeEquals(webhookSecret, token)) {
            logger.warn("Invalid webhook token provided");
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, 
                "UNAUTHORIZED", "Invalid token");
            return;
        }

        logger.debug("Webhook request authenticated successfully");
        filterChain.doFilter(request, response);
    }

    /**
     * Send JSON error response with proper content type.
     */
    private void sendErrorResponse(HttpServletResponse response, int status, 
                                   String error, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        
        Map<String, String> errorBody = Map.of(
            "error", error,
            "message", message
        );
        
        response.getWriter().write(objectMapper.writeValueAsString(errorBody));
    }

    /**
     * Constant-time string comparison to prevent timing attacks.
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}

