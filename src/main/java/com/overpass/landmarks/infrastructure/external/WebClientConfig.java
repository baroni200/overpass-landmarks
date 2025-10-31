package com.overpass.landmarks.infrastructure.external;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WebClient configuration.
 * 
 * In Spring Boot 3.x, Jackson codecs are automatically configured,
 * so explicit codec configuration is not needed.
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        // Spring Boot auto-configures Jackson codecs for WebClient
        // No need to manually configure Jackson2JsonEncoder/Decoder
        return WebClient.builder();
    }
}

