package com.overpass.landmarks.presentation.controller;

import com.overpass.landmarks.application.dto.WebhookRequestDto;
import com.overpass.landmarks.application.dto.WebhookResponseDto;
import com.overpass.landmarks.application.service.WebhookService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for webhook endpoint.
 * Processes coordinate requests and triggers Overpass API queries.
 */
@RestController
@RequestMapping("/webhook")
public class WebhookController {

    private static final Logger logger = LoggerFactory.getLogger(WebhookController.class);

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    /**
     * POST /webhook
     * 
     * Protected endpoint that receives coordinates, transforms them,
     * queries Overpass API, persists results, and populates cache.
     * 
     * Processing: Synchronous
     * 
     * @param request Webhook request with lat/lng coordinates
     * @return Response with transformed key, landmark count, and radius
     */
    @PostMapping
    public ResponseEntity<WebhookResponseDto> handleWebhook(@Valid @RequestBody WebhookRequestDto request) {
        logger.info("Received webhook request: lat={}, lng={}", request.getLat(), request.getLng());
        WebhookResponseDto response = webhookService.processWebhook(request.getLat(), request.getLng());
        return ResponseEntity.ok(response);
    }
}
