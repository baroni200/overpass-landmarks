package com.overpass.landmarks.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration that parses Railway's DATABASE_URL environment variable
 * and converts it to Spring Boot datasource properties.
 * 
 * This runs early in the Spring Boot startup process via ApplicationListener.
 */
@Configuration
public class RailwayDatabaseConfig implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    private static final Logger logger = LoggerFactory.getLogger(RailwayDatabaseConfig.class);

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment environment = event.getEnvironment();
        
        // Check system environment variables directly (most reliable)
        String databaseUrl = System.getenv("DATABASE_URL");
        
        // Log for debugging
        if (databaseUrl != null && !databaseUrl.isEmpty()) {
            logger.info("DATABASE_URL found: {}", databaseUrl.substring(0, Math.min(50, databaseUrl.length())) + "...");
        } else {
            logger.warn("DATABASE_URL not found in environment variables!");
        }

        if (databaseUrl == null || databaseUrl.isEmpty()) {
            databaseUrl = environment.getProperty("DATABASE_URL");
        }

        if (databaseUrl != null && !databaseUrl.isEmpty()) {
            // Check if Spring Boot properties are already set (they take precedence)
            String springDatasourceUrl = System.getenv("SPRING_DATASOURCE_URL");
            if (springDatasourceUrl == null || springDatasourceUrl.isEmpty()) {
                springDatasourceUrl = environment.getProperty("SPRING_DATASOURCE_URL");
            }
            
            if (springDatasourceUrl == null || springDatasourceUrl.isEmpty()) {
                try {
                    logger.info("Found DATABASE_URL environment variable, parsing Railway connection string");
                    Map<String, Object> properties = parseDatabaseUrl(databaseUrl);
                    
                    // Add properties to environment with high priority
                    environment.getPropertySources().addFirst(
                        new MapPropertySource("railwayDatabase", properties)
                    );
                    
                    logger.info("Configured datasource from DATABASE_URL: url={}", properties.get("spring.datasource.url"));
                } catch (Exception e) {
                    logger.error("Failed to parse DATABASE_URL: {}", databaseUrl, e);
                    // Fall back to default Spring Boot properties
                }
            } else {
                logger.debug("SPRING_DATASOURCE_URL is set, using it instead of DATABASE_URL");
            }
        } else {
            logger.warn("DATABASE_URL not found in environment variables. Check Railway configuration.");
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

