package com.example.wms.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * Redis cache manager configuration.
 *
 * <p>Overrides Spring Boot's default Java-serialisation cache with a
 * Jackson-JSON serialiser so cached values are human-readable in Redis
 * and not tied to a specific JVM version.
 *
 * <p>Per-cache TTLs match the Redis key schema defined in CLAUDE.md:
 * <ul>
 *   <li>{@code system-config} — 5 minutes (evicted explicitly on update)</li>
 *   <li>{@code delivery-eligible} — 5 minutes (evicted on truck change / schedule)</li>
 *   <li>{@code sla-report} — 5 minutes (evicted on any order status change)</li>
 * </ul>
 */
@Configuration
public class RedisConfig {

    private static final Duration TTL_5_MIN = Duration.ofMinutes(5);

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = defaultCacheConfig();

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(Map.of(
                        "system-config",       defaultConfig.entryTtl(TTL_5_MIN),
                        "delivery-eligible",   defaultConfig.entryTtl(TTL_5_MIN),
                        "sla-report",          defaultConfig.entryTtl(TTL_5_MIN),
                        "throughput-report",   defaultConfig.entryTtl(TTL_5_MIN)
                ))
                .build();
    }

    private RedisCacheConfiguration defaultCacheConfig() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        // Store type info so Jackson can deserialise polymorphic values correctly.
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(mapper);

        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(TTL_5_MIN)
                .disableCachingNullValues()
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                jsonSerializer));
    }
}
