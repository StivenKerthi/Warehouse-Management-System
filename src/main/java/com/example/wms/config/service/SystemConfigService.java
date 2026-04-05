package com.example.wms.config.service;

import com.example.wms.config.dto.SystemConfigDto;
import com.example.wms.config.dto.UpdateConfigRequest;
import com.example.wms.config.model.SystemConfig;
import com.example.wms.config.repository.SystemConfigRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private final SystemConfigRepository configRepository;

    // -------------------------------------------------------------------------
    // Read — all entries (no cache; list is tiny and infrequently requested)
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<SystemConfigDto> findAll() {
        return configRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    // -------------------------------------------------------------------------
    // Read — single value by key (cached for 5 min; used by delivery scheduling)
    // -------------------------------------------------------------------------

    /**
     * Returns the string value for the given config key.
     *
     * <p>Result is cached under {@code system-config::<key>} in Redis with a
     * 5-minute TTL. Cache is evicted whenever {@link #update} is called for
     * the same key.
     *
     * @throws EntityNotFoundException if the key does not exist in the DB
     */
    @Cacheable(cacheNames = "system-config", key = "#key")
    @Transactional(readOnly = true)
    public String getValue(String key) {
        return getOrThrow(key).getConfigValue();
    }

    /**
     * Convenience method for callers that need an integer config value.
     *
     * @param key          the config key
     * @param defaultValue fallback used if the stored value cannot be parsed
     */
    @Transactional(readOnly = true)
    public int getIntValue(String key, int defaultValue) {
        try {
            return Integer.parseInt(getValue(key));
        } catch (NumberFormatException | EntityNotFoundException e) {
            return defaultValue;
        }
    }

    // -------------------------------------------------------------------------
    // Write — update existing key (evicts cache entry for that key)
    // -------------------------------------------------------------------------

    /**
     * Updates the value of an existing config key and evicts its Redis cache entry.
     *
     * <p>Creating new keys via the API is intentionally disallowed — keys are
     * managed by Flyway migrations only.
     *
     * @throws EntityNotFoundException if the key does not exist
     */
    @CacheEvict(cacheNames = "system-config", key = "#request.key()")
    @Transactional
    public SystemConfigDto update(UpdateConfigRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        SystemConfig config = getOrThrow(request.key());
        config.setConfigValue(request.value());
        config.setUpdatedBy(username);
        config.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        return toDto(configRepository.save(config));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private SystemConfig getOrThrow(String key) {
        return configRepository.findById(key)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Config key not found: " + key));
    }

    private SystemConfigDto toDto(SystemConfig config) {
        return new SystemConfigDto(
                config.getConfigKey(),
                config.getConfigValue(),
                config.getUpdatedBy(),
                config.getUpdatedAt()
        );
    }
}
