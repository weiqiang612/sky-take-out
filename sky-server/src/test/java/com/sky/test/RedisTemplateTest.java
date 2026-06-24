package com.sky.test;

import com.sky.config.RedisConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class RedisTemplateTest {

    @Test
    void redisTemplateShouldUseProjectSerializers() {
        // given
        final RedisConfiguration configuration = new RedisConfiguration();
        final RedisConnectionFactory redisConnectionFactory = mock(RedisConnectionFactory.class);

        // when
        final RedisTemplate<String, Object> redisTemplate = configuration.redisTemplate(redisConnectionFactory);

        // then
        assertNotNull(redisTemplate);
        assertInstanceOf(StringRedisSerializer.class, redisTemplate.getKeySerializer());
        assertInstanceOf(StringRedisSerializer.class, redisTemplate.getHashKeySerializer());
        assertInstanceOf(GenericJackson2JsonRedisSerializer.class, redisTemplate.getValueSerializer());
        assertInstanceOf(GenericJackson2JsonRedisSerializer.class, redisTemplate.getHashValueSerializer());
    }

    @Test
    void cacheManagerShouldBeRedisCacheManager() {
        // given
        final RedisConfiguration configuration = new RedisConfiguration();
        final RedisConnectionFactory redisConnectionFactory = mock(RedisConnectionFactory.class);

        // when
        final CacheManager cacheManager = configuration.cacheManager(redisConnectionFactory);

        // then
        assertNotNull(cacheManager);
        assertInstanceOf(RedisCacheManager.class, cacheManager);
    }
}
