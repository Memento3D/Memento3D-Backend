package com.memento3d.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayConfig {
    @Value("${app.database.flyway-repair-before-migrate:false}")
    private boolean repairBeforeMigrate;

    /**
     * Only explicitly opted-in maintenance may repair before migrate; never automatically
     * purge any transient/failed migration state from flyway_schema_history
     * before applying pending migrations.
     */
    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            if (repairBeforeMigrate) flyway.repair();
            flyway.migrate();
        };
    }
}
