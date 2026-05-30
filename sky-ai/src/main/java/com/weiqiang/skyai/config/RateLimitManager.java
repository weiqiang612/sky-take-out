package com.weiqiang.skyai.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitManager {

    private final RedissonClient redissonClient;
    private final RateLimitProperties properties;

    /**
     * 尝试获取一个限流令牌
     *
     * @param userId 用户 ID
     * @return true - 获取成功，正常访问；false - 获取失败，限流拦截
     */
    public boolean tryAcquire(String userId) {
        String key = "rate_limit:ai:" + userId;
        try {
            RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
            if (!rateLimiter.isExists()) {
                // 设置滑动窗口规则：OVERALL 模式，在给定秒数窗口内，最多允许指定次数的请求
                rateLimiter.trySetRate(
                        RateType.OVERALL,
                        properties.getRequestsPerMinute(),
                        properties.getWindowSeconds(),
                        RateIntervalUnit.SECONDS
                );
                // 显式设置 Redis Key 过期时间，规避没有显式过期导致的缓存泄露
                rateLimiter.expire(Duration.ofSeconds(properties.getWindowSeconds()));
            }
            return rateLimiter.tryAcquire(1);
        } catch (Exception e) {
            log.error("Failed to acquire rate limit token for user: {}", userId, e);
            // Redis 宕机或连接异常时的优雅降级策略：允许访问，以保护业务可用性
            return true;
        }
    }

    /**
     * 获取限制解除所需的等待秒数
     *
     * @param userId 用户 ID
     * @return 限制解除的估算剩余秒数
     */
    public long getRetryAfterSeconds(String userId) {
        String key = "rate_limit:ai:" + userId;
        try {
            // 获取该用户对应限流组件的 TTL（remainTimeToLive 返回值单位是毫秒）
            long ttl = redissonClient.getKeys().remainTimeToLive(key);
            if (ttl <= 0) {
                return properties.getWindowSeconds();
            }
            // 将毫秒转成秒，且保证至少返回 1 秒以支持前端或 HTTP 的 Retry-After
            return Math.max(1, ttl / 1000);
        } catch (Exception e) {
            return properties.getWindowSeconds();
        }
    }
}
