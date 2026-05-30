package com.weiqiang.skyai.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RKeys;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitManagerTest {

    private RedissonClient redissonClient;
    private RateLimitProperties properties;
    private RateLimitManager manager;

    @BeforeEach
    void setUp() {
        redissonClient = mock(RedissonClient.class);
        properties = new RateLimitProperties();
        properties.setRequestsPerMinute(5);
        properties.setWindowSeconds(30);
        manager = new RateLimitManager(redissonClient, properties);
    }

    @Test
    void shouldCreateLimiterWithPropertiesOnFirstAcquire() {
        RRateLimiter rateLimiter = mock(RRateLimiter.class);
        when(redissonClient.getRateLimiter("rate_limit:ai:user123")).thenReturn(rateLimiter);
        when(rateLimiter.isExists()).thenReturn(false);
        when(rateLimiter.tryAcquire(1)).thenReturn(true);

        boolean result = manager.tryAcquire("user123");

        assertTrue(result);
        // 验证是否在不存在时执行了滑动窗口属性的初始化注册及显式的 TTL 保护设置
        verify(rateLimiter).trySetRate(RateType.OVERALL, 5, 30, RateIntervalUnit.SECONDS);
        verify(rateLimiter).expire(Duration.ofSeconds(30));
    }

    @Test
    void shouldReturnFalseWhenAcquireFails() {
        RRateLimiter rateLimiter = mock(RRateLimiter.class);
        when(redissonClient.getRateLimiter("rate_limit:ai:user123")).thenReturn(rateLimiter);
        when(rateLimiter.isExists()).thenReturn(true);
        when(rateLimiter.tryAcquire(1)).thenReturn(false);

        boolean result = manager.tryAcquire("user123");

        assertFalse(result);
    }

    @Test
    void shouldGetRetryAfterSecondsBasedOnTtl() {
        RKeys keys = mock(RKeys.class);
        when(redissonClient.getKeys()).thenReturn(keys);
        // remainTimeToLive 返回的是毫秒数，15000 毫秒对应 15 秒
        when(keys.remainTimeToLive("rate_limit:ai:user123")).thenReturn(15000L);

        long retryAfter = manager.getRetryAfterSeconds("user123");

        assertEquals(15, retryAfter);
    }
}
