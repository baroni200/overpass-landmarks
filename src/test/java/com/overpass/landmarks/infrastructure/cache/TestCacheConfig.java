package com.overpass.landmarks.infrastructure.cache;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test cache configuration using in-memory ConcurrentMapCacheManager.
 * This replaces Redis cache for tests to avoid requiring Redis infrastructure.
 */
@TestConfiguration
@EnableCaching
public class TestCacheConfig {

    @Bean
    @Primary
    public CacheManager testCacheManager() {
        // Use ConcurrentMapCacheManager for tests (in-memory, no Redis required)
        return new ConcurrentMapCacheManager("landmarks", "coordinateRequests");
    }
}
