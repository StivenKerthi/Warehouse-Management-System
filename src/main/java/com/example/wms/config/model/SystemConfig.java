package com.example.wms.config.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * JPA entity for the {@code system_config} table.
 *
 * <p>Rows are seeded by Flyway (V2__create_system_config.sql) and managed at
 * runtime via {@code PUT /api/admin/config}. No new keys are created through
 * the API — only existing rows are updated.
 *
 * <p>{@code updatedAt} is set explicitly in the service layer before every
 * {@code save()} call rather than via {@code @PreUpdate}, because this entity
 * is seeded by Flyway and the lifecycle annotations would fire on the first
 * load-then-save even when no logical change has occurred.
 */
@Entity
@Table(name = "system_config")
@Getter
@Setter
public class SystemConfig {

    @Id
    @Column(name = "config_key", nullable = false, length = 100)
    private String configKey;

    @Column(name = "config_value", nullable = false, length = 500)
    private String configValue;

    /** Username of the last admin who changed this row; {@code null} for seed data. */
    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
