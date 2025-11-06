package com.overpass.landmarks.module.test.support;

import org.springframework.beans.factory.DisposableBean;
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
public class TestcontainersConfig implements DisposableBean {

    private PostgreSQLContainer<?> container;

    /**
     * PostgreSQL container for integration tests.
     * Automatically started and stopped by Testcontainers lifecycle management.
     */
    @Bean(initMethod = "start", destroyMethod = "stop")
    @SuppressWarnings("resource")
    public PostgreSQLContainer<?> postgresContainer() {
        PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:14-alpine"))
                .withDatabaseName("overpass_test")
                .withUsername("test")
                .withPassword("test")
                .withReuse(true);
        container = postgres;
        return postgres;
    }

    @Override
    public void destroy() {
        if (container != null) {
            container.close();
        }
    }
}

