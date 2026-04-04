package com.example.wms;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Smoke test — verifies the Spring application context assembles without
 * connecting to any real infrastructure (PostgreSQL, Redis, Kafka).
 *
 * H2 stands in for PostgreSQL; Redis and Kafka auto-configurations are excluded.
 * This test must stay green at all times — if it fails, the project won't start.
 */
@SpringBootTest
@TestPropertySource(properties = {
        // ── In-memory DB (H2 replaces PostgreSQL) ──────────────────────────
        "spring.datasource.url=jdbc:h2:mem:wms-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;" +
                "INIT=CREATE DOMAIN IF NOT EXISTS user_role AS VARCHAR(50)",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",

        // ── Let Hibernate create the schema from entities (no Flyway in tests) ─
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",

        // ── H2-compatible dialect ──────────────────────────────────────────
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",

        // ── Disable Redis cache (no Redis available in unit test) ──────────
        "spring.cache.type=none",

        // ── Point Kafka at a port that will never answer (excluded anyway) ─
        "spring.kafka.bootstrap-servers=localhost:19092",

        // ── Exclude all infrastructure auto-configurations ─────────────────
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration," +
                "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
class WmsApplicationTest {

    @Test
    void contextLoads() {
        // Passes if the Spring context starts without throwing.
    }
}
