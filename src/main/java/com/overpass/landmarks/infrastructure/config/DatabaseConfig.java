package com.overpass.landmarks.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Database configuration that handles Railway's DATABASE_URL format.
 * Railway provides DATABASE_URL in format: postgresql://user:password@host:port/database
 * This configuration parses it and sets Spring Boot datasource properties.
 */
@Configuration
public class DatabaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties dataSourceProperties() {
        DataSourceProperties properties = new DataSourceProperties();
        
        // Check if DATABASE_URL is set (Railway provides this)
        String databaseUrl = System.getenv("DATABASE_URL");
        if (databaseUrl != null && !databaseUrl.isEmpty()) {
            // Check if Spring Boot properties are already set (they take precedence)
            String springDatasourceUrl = System.getenv("SPRING_DATASOURCE_URL");
            if (springDatasourceUrl == null || springDatasourceUrl.isEmpty()) {
                try {
                    logger.info("Found DATABASE_URL environment variable, parsing Railway connection string");
                    parseDatabaseUrl(databaseUrl, properties);
                } catch (URISyntaxException e) {
                    logger.error("Failed to parse DATABASE_URL: {}", databaseUrl, e);
                    // Fall back to default Spring Boot properties
                }
            } else {
                logger.debug("SPRING_DATASOURCE_URL is set, using it instead of DATABASE_URL");
            }
        } else {
            logger.debug("DATABASE_URL not found, using SPRING_DATASOURCE_* environment variables");
        }
        
        return properties;
    }

    /**
     * Parse Railway's DATABASE_URL format: postgresql://user:password@host:port/database
     */
    private void parseDatabaseUrl(String databaseUrl, DataSourceProperties properties) throws URISyntaxException {
        // Railway's DATABASE_URL format: postgresql://user:password@host:port/database
        URI uri = new URI(databaseUrl);
        
        String scheme = uri.getScheme();
        if (!"postgresql".equals(scheme) && !"postgres".equals(scheme)) {
            throw new IllegalArgumentException("Unsupported database scheme: " + scheme);
        }
        
        String host = uri.getHost();
        int port = uri.getPort() > 0 ? uri.getPort() : 5432;
        String path = uri.getPath();
        String database = path != null && path.startsWith("/") ? path.substring(1) : path;
        
        String userInfo = uri.getUserInfo();
        String username = null;
        String password = null;
        if (userInfo != null) {
            int colonIndex = userInfo.indexOf(':');
            if (colonIndex >= 0) {
                username = userInfo.substring(0, colonIndex);
                password = userInfo.substring(colonIndex + 1);
            } else {
                username = userInfo;
            }
        }
        
        // Build JDBC URL
        String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s", host, port, database);
        
        // Handle query parameters (e.g., ?sslmode=require)
        String query = uri.getQuery();
        if (query != null && !query.isEmpty()) {
            jdbcUrl += "?" + query;
        }
        
        properties.setUrl(jdbcUrl);
        if (username != null) {
            properties.setUsername(username);
        }
        if (password != null) {
            properties.setPassword(password);
        }
        
        logger.info("Configured datasource from DATABASE_URL: host={}, port={}, database={}, username={}", 
            host, port, database, username);
    }
}

