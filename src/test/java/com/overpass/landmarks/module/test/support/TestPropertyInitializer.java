package com.overpass.landmarks.module.test.support;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Application context initializer for test properties.
 * Can be used to set test-specific properties.
 * 
 * Note: For Spring Boot 3.x, prefer using @TestPropertySource or application-test.yml
 * instead of programmatic property initialization.
 */
public class TestPropertyInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        // Properties can be set here if needed
        // For Spring Boot 3.x, prefer @TestPropertySource annotation
    }
}
