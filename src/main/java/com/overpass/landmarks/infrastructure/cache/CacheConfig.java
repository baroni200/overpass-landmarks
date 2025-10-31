package com.overpass.landmarks.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Cache configuration using Caffeine (in-JVM cache).
 * 
 * Choice: Caffeine over Redis
 * Rationale:
 * - Simpler setup (no external dependency)
 * - Good performance for single-instance deployments
 * - Zero configuration overhead
 * - Suitable for coding challenge/prototype
 * 
 * Note: For production multi-instance deployments, Redis would be preferred.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("landmarks");
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(600, TimeUnit.SECONDS) // Default TTL, can be overridden via application.yml
            .recordStats());
        return cacheManager;
    }
}

