package com.memento3d.shared.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import static org.junit.jupiter.api.Assertions.*;

class RedisConfigTest {

    private RedisConfig redisConfig;
    private RedisConnectionFactory connectionFactory;

    @BeforeEach
    void setUp() {
        redisConfig = new RedisConfig();
        connectionFactory = Mockito.mock(RedisConnectionFactory.class);
    }

    @Test
    @DisplayName("redisTemplate bean configures String key serializer and JSON value serializer")
    void testRedisTemplate() {
        RedisTemplate<String, Object> template = redisConfig.redisTemplate(connectionFactory);

        assertNotNull(template);
        assertEquals(connectionFactory, template.getConnectionFactory());
        assertTrue(template.getKeySerializer() instanceof StringRedisSerializer);
        assertTrue(template.getValueSerializer() instanceof GenericJackson2JsonRedisSerializer);
        assertTrue(template.getHashKeySerializer() instanceof StringRedisSerializer);
        assertTrue(template.getHashValueSerializer() instanceof GenericJackson2JsonRedisSerializer);
    }

    @Test
    @DisplayName("stringRedisTemplate bean creates StringRedisTemplate")
    void testStringRedisTemplate() {
        StringRedisTemplate template = redisConfig.stringRedisTemplate(connectionFactory);

        assertNotNull(template);
        assertEquals(connectionFactory, template.getConnectionFactory());
    }

    @Test
    @DisplayName("cacheManager bean creates RedisCacheManager")
    void testCacheManager() {
        RedisCacheManager cacheManager = redisConfig.cacheManager(connectionFactory);

        assertNotNull(cacheManager);
    }

    @Test
    @DisplayName("errorHandler bean creates CustomRedisCacheErrorHandler")
    void testErrorHandler() {
        CacheErrorHandler handler = redisConfig.errorHandler();

        assertNotNull(handler);
        assertTrue(handler instanceof RedisConfig.CustomRedisCacheErrorHandler);

        assertDoesNotThrow(() -> handler.handleCacheGetError(new RuntimeException("Redis down"), null, "key"));
        assertDoesNotThrow(() -> handler.handleCachePutError(new RuntimeException("Redis down"), null, "key", "val"));
        assertDoesNotThrow(() -> handler.handleCacheEvictError(new RuntimeException("Redis down"), null, "key"));
        assertDoesNotThrow(() -> handler.handleCacheClearError(new RuntimeException("Redis down"), null));
    }
}

