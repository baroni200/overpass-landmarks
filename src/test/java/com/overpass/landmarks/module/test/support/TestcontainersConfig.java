package com.overpass.landmarks.module.test.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers configuration for integration tests.
 * Provides PostgreSQL container for testing.
 * 
 * Usage: Import this configuration in your test classes with @Import(TestcontainersConfig.class)
 */
@TestConfiguration
public class TestcontainersConfig {

    /**
     * PostgreSQL container for integration tests.
     * Automatically started and stopped by Testcontainers lifecycle management.
     */
    @Bean(initMethod = "start", destroyMethod = "stop")
    public PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:14-alpine"))
                .withDatabaseName("overpass_test")
                .withUsername("test")
                .withPassword("test")
                .withReuse(true);
    }
}

