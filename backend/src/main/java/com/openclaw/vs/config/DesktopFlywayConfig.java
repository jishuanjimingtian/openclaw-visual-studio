package com.openclaw.vs.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Desktop installs reuse a persistent H2 file under userData. Repair checksum drift
 * before migrate so a rebuilt app JAR does not fail when migration files changed in dev.
 */
@Configuration
@Profile("desktop")
public class DesktopFlywayConfig {

    @Bean
    public FlywayMigrationStrategy desktopFlywayMigrationStrategy() {
        return (Flyway flyway) -> {
            flyway.repair();
            flyway.migrate();
        };
    }
}
