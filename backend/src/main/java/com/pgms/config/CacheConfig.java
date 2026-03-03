package com.pgms.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    @Primary
    @ConditionalOnProperty(name = "app.cache.provider", havingValue = "redis")
    public CacheManager redisCacheManager(
            RedisConnectionFactory redisConnectionFactory,
            @Value("${app.cache.ttl-seconds:60}") long ttlSeconds
    ) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(Math.max(ttlSeconds, 1)))
                .disableCachingNullValues()
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer()
                        )
                );

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(config)
                .initialCacheNames(new HashSet<>(cacheNames()))
                .build();
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "app.cache.provider", havingValue = "local", matchIfMissing = true)
    public CacheManager cacheManager(
            @Value("${app.cache.ttl-seconds:60}") long ttlSeconds,
            @Value("${app.cache.maximum-size:500}") long maxSize
    ) {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCacheNames(cacheNames());
        cacheManager.setCaffeine(
                Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofSeconds(Math.max(ttlSeconds, 1)))
                        .maximumSize(Math.max(maxSize, 100))
        );
        return cacheManager;
    }

    private List<String> cacheNames() {
        return List.of(
                CacheNames.TENANTS_ALL,
                CacheNames.TENANTS_ACTIVE,
                CacheNames.TENANTS_DAILY,
                CacheNames.TENANTS_DELETED,
                CacheNames.ACCOUNTS,
                CacheNames.ROOMS,
                CacheNames.EXPENSES,
                CacheNames.RENTS_DUE,
                CacheNames.RENTS_COLLECTED,
                CacheNames.RENT_DASHBOARD
        );
    }
}
