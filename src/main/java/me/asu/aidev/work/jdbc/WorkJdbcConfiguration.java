package me.asu.aidev.work.jdbc;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WorkJdbcConfiguration {
    @Bean
    WorkDatabaseDialect workDatabaseDialect(@Value("${aidev.database.mode:postgresql}") String mode) {
        return switch (mode.trim().toLowerCase()) {
            case "sqlite" -> new SqliteWorkDatabaseDialect();
            case "postgresql", "postgres" -> new PostgresWorkDatabaseDialect();
            default -> throw new IllegalArgumentException("unsupported aidev.database.mode: " + mode);
        };
    }
}
