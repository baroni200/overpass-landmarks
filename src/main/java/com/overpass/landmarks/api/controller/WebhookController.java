package com.overpass.landmarks.api.controller;

import com.overpass.landmarks.api.dto.WebhookRequestDto;
import com.overpass.landmarks.api.dto.WebhookResponseDto;
import com.overpass.landmarks.api.dto.WebhookSubmissionResponseDto;
import com.overpass.landmarks.application.service.WebhookService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

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
     * Protected endpoint that receives coordinates and returns immediately with a
     * request ID.
     * Processing happens asynchronously in the background.
     * 
     * Processing: Asynchronous
     * 
     * @param request Webhook request with lat/lng coordinates
     * @return Response with request ID and status (PENDING)
     */
    @PostMapping
    public ResponseEntity<WebhookSubmissionResponseDto> handleWebhook(@Valid @RequestBody WebhookRequestDto request) {
        logger.info("Received webhook request: lat={}, lng={}", request.getLat(), request.getLng());
        WebhookSubmissionResponseDto response = webhookService.submitWebhook(request.getLat(), request.getLng());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * GET /webhook/{id}
     * 
     * Retrieves the result of a webhook request by ID.
     * 
     * @param id The request ID returned from POST /webhook
     * @return Response with landmarks if processing is complete, 404 if not found,
     *         202 if still pending
     */
    @GetMapping("/{id}")
    public ResponseEntity<WebhookResponseDto> getWebhookStatus(@PathVariable UUID id) {
        logger.info("Retrieving webhook status for ID: {}", id);
        return webhookService.getWebhookStatus(id)
                .map(response -> ResponseEntity.ok(response))
                .orElse(ResponseEntity.status(HttpStatus.ACCEPTED).build());
    }
}
