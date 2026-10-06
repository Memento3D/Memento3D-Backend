package com.memeto.team.memeto3d.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class RedisConfigTest {

    private RedisConfig redisConfig;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        redisConfig = new RedisConfig();
        ReflectionTestUtils.setField(redisConfig, "redisHost", "localhost");
        ReflectionTestUtils.setField(redisConfig, "redisPort", 6379);
        ReflectionTestUtils.setField(redisConfig, "defaultTtl", Duration.ofMillis(3600000));

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("redisConnectionFactory bean creates LettuceConnectionFactory with configured host/port")
    void testRedisConnectionFactory() {
        RedisConnectionFactory factory = redisConfig.redisConnectionFactory();
        assertNotNull(factory);
        assertTrue(factory instanceof LettuceConnectionFactory);
        LettuceConnectionFactory lettuceFactory = (LettuceConnectionFactory) factory;
        assertEquals("localhost", lettuceFactory.getHostName());
        assertEquals(6379, lettuceFactory.getPort());
    }

    @Test
    @DisplayName("redisConnectionFactory parses rediss:// URL for Upstash Redis in production")
    void testRedisConnectionFactoryWithRedisUrl() {
        String url = "rediss://default:secretPassword123@absolute-tomcat-224856.upstash.io:6379";
        ReflectionTestUtils.setField(redisConfig, "redisUrl", url);

        RedisConnectionFactory factory = redisConfig.redisConnectionFactory();
        assertNotNull(factory);
        assertTrue(factory instanceof LettuceConnectionFactory);
        LettuceConnectionFactory lettuceFactory = (LettuceConnectionFactory) factory;
        assertEquals("absolute-tomcat-224856.upstash.io", lettuceFactory.getHostName());
        assertEquals(6379, lettuceFactory.getPort());
        assertEquals("default", lettuceFactory.getStandaloneConfiguration().getUsername());
        assertEquals("secretPassword123", String.valueOf(lettuceFactory.getStandaloneConfiguration().getPassword().get()));
        assertTrue(lettuceFactory.isUseSsl());
    }

    @Test
    @DisplayName("redisTemplate bean configures String key serializer and JSON value serializer")
    void testRedisTemplate() {
        RedisConnectionFactory factory = redisConfig.redisConnectionFactory();
        RedisTemplate<String, Object> template = redisConfig.redisTemplate(factory, objectMapper);

        assertNotNull(template);
        assertNotNull(template.getConnectionFactory());
        assertTrue(template.getKeySerializer() instanceof StringRedisSerializer);
        assertTrue(template.getValueSerializer() instanceof GenericJackson2JsonRedisSerializer);
        assertTrue(template.getHashKeySerializer() instanceof StringRedisSerializer);
        assertTrue(template.getHashValueSerializer() instanceof GenericJackson2JsonRedisSerializer);
    }

    @Test
    @DisplayName("cacheManager bean creates RedisCacheManager")
    void testCacheManager() {
        RedisConnectionFactory factory = redisConfig.redisConnectionFactory();
        RedisCacheManager cacheManager = redisConfig.cacheManager(factory, objectMapper);

        assertNotNull(cacheManager);
    }
}
