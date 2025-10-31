package com.overpass.landmarks;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableCaching
@EnableJpaAuditing
public class OverpassLandmarksApplication {

    public static void main(String[] args) {
        // Log DATABASE_URL early to debug Railway configuration
        String databaseUrl = System.getenv("DATABASE_URL");
        if (databaseUrl != null && !databaseUrl.isEmpty()) {
            System.out.println(
                    "DATABASE_URL is set: " + databaseUrl.substring(0, Math.min(50, databaseUrl.length())) + "...");
        } else {
            System.out.println("WARNING: DATABASE_URL is NOT set in environment variables!");
        }

        SpringApplication.run(OverpassLandmarksApplication.class, args);
    }
}
