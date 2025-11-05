package com.overpass.landmarks.presentation.controller;

import com.overpass.landmarks.application.dto.WebhookAcknowledgmentDto;
import com.overpass.landmarks.application.dto.WebhookEventDto;
import com.overpass.landmarks.application.dto.WebhookRequestDto;
import com.overpass.landmarks.infrastructure.messaging.WebhookProducer;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for webhook endpoint.
 * Receives coordinate requests and queues them for asynchronous processing via
 * Kafka.
 */
@RestController
@RequestMapping("/webhook")
public class WebhookController {

    private static final Logger logger = LoggerFactory.getLogger(WebhookController.class);

    private final WebhookProducer webhookProducer;

    public WebhookController(WebhookProducer webhookProducer) {
        this.webhookProducer = webhookProducer;
    }

    /**
     * POST /webhook
     * 
     * Protected endpoint that receives coordinates and queues them for
     * asynchronous processing via Kafka.
     * 
     * Processing: Asynchronous (via Kafka queue)
     * 
     * @param request Webhook request with lat/lng coordinates
     * @return Acknowledgment response indicating the request was queued
     */
    @PostMapping
    public ResponseEntity<WebhookAcknowledgmentDto> handleWebhook(@Valid @RequestBody WebhookRequestDto request) {
        logger.info("Received webhook request: lat={}, lng={}", request.getLat(), request.getLng());

        // Create webhook event
        WebhookEventDto event = new WebhookEventDto(request.getLat(), request.getLng());

        // Send to Kafka for async processing
        webhookProducer.sendWebhookEvent(event);

        // Return immediate acknowledgment
        WebhookAcknowledgmentDto acknowledgment = new WebhookAcknowledgmentDto(event.getId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(acknowledgment);
    }
}
