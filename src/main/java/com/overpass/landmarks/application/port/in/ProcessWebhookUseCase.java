package com.overpass.landmarks.application.port.in;

import com.overpass.landmarks.api.dto.WebhookResponseDto;

import java.math.BigDecimal;

/**
 * Input port for processing webhook requests.
 * Defines the use case interface for the webhook service.
 */
public interface ProcessWebhookUseCase {

  /**
   * Process webhook request: transform coordinates, query Overpass, persist, and
   * cache.
   * 
   * Idempotency: If a request with the same transformed key already exists,
   * returns the existing result without querying Overpass again.
   * 
   * @param lat Latitude
   * @param lng Longitude
   * @return Webhook response with transformed key, landmark count, and radius
   */
  WebhookResponseDto processWebhook(BigDecimal lat, BigDecimal lng);
}
