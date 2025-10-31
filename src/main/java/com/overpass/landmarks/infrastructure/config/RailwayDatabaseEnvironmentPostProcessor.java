package com.overpass.landmarks.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Environment post-processor that parses Railway's DATABASE_URL environment variable
 * and converts it to Spring Boot datasource properties before Spring Boot processes them.
 * 
 * This runs early in the Spring Boot startup process, before DataSource auto-configuration.
 */
public class RailwayDatabaseEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RailwayDatabaseEnvironmentPostProcessor.class);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // Check if DATABASE_URL is set (Railway provides this)
        String databaseUrl = environment.getProperty("DATABASE_URL");
        if (databaseUrl == null || databaseUrl.isEmpty()) {
            databaseUrl = System.getenv("DATABASE_URL");
        }

        if (databaseUrl != null && !databaseUrl.isEmpty()) {
            // Check if Spring Boot properties are already set (they take precedence)
            String springDatasourceUrl = environment.getProperty("SPRING_DATASOURCE_URL");
            if (springDatasourceUrl == null || springDatasourceUrl.isEmpty()) {
                try {
                    logger.info("Found DATABASE_URL environment variable, parsing Railway connection string");
                    Map<String, Object> properties = parseDatabaseUrl(databaseUrl);
                    
                    // Add properties to environment with high priority
                    environment.getPropertySources().addFirst(
                        new MapPropertySource("railwayDatabase", properties)
                    );
                    
                    logger.info("Configured datasource from DATABASE_URL: host={}, database={}", 
                        properties.get("spring.datasource.url"));
                } catch (Exception e) {
                    logger.error("Failed to parse DATABASE_URL: {}", databaseUrl, e);
                    // Fall back to default Spring Boot properties
                }
            } else {
                logger.debug("SPRING_DATASOURCE_URL is set, using it instead of DATABASE_URL");
            }
        } else {
            logger.debug("DATABASE_URL not found, using SPRING_DATASOURCE_* environment variables or defaults");
        }
    }

    /**
     * Parse Railway's DATABASE_URL format: postgresql://user:password@host:port/database
     */
    private Map<String, Object> parseDatabaseUrl(String databaseUrl) throws Exception {
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
        
        Map<String, Object> properties = new HashMap<>();
        properties.put("spring.datasource.url", jdbcUrl);
        if (username != null) {
            properties.put("spring.datasource.username", username);
        }
        if (password != null) {
            properties.put("spring.datasource.password", password);
        }
        
        logger.info("Parsed DATABASE_URL: host={}, port={}, database={}, username={}", 
            host, port, database, username);
        
        return properties;
    }
}

