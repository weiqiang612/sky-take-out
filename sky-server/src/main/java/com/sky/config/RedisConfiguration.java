package com.sky.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.sky.json.JacksonObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * @author weiqiang
 * @version 1.0
 * @Date 2026/3/15 15:24
 */

/**
 * redis配置类
 */
@EnableCaching
@Configuration
@Slf4j
public class RedisConfiguration {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        log.info("开始构建Redis缓存管理器...");
        // 1. 构造和你 redisTemplate 一模一样的 ObjectMapper
        JacksonObjectMapper jacksonObjectMapper = new JacksonObjectMapper();
        jacksonObjectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        // 2. 使用这个 ObjectMapper 创建序列化器
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(jacksonObjectMapper);

        // 3. 配置 RedisCacheConfiguration
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                // 设置 Key 的序列化方式为 String
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                // 设置 Value 的序列化方式为 JSON（使用你自定义的规则）
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                // 【建议】设置默认过期时间，例如 1 小时，防止缓存永久堆积
                .entryTtl(Duration.ofHours(1))
                // 不缓存空值
                .disableCachingNullValues();

        // 4. 构建并返回 CacheManager
        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(config)
                .build();
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        log.info("开始创建redis模板对象...");
        RedisTemplate redisTemplate = new RedisTemplate<>();
        // 设置redis连接工厂对象
        redisTemplate.setConnectionFactory(redisConnectionFactory);

        // 1. 使用自定义对象映射器
        JacksonObjectMapper jacksonObjectMapper = new JacksonObjectMapper();
        // 2. 关键：虽然你的类处理了时间，但为了让 Redis 能反序列化回 List<DishVO>，
        // 依然需要开启类型保留（Type Id）
        jacksonObjectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        // 3. 将对象映射器传给序列化器
        GenericJackson2JsonRedisSerializer genericJackson2JsonRedisSerializer = new GenericJackson2JsonRedisSerializer(jacksonObjectMapper);


        // 设置redis key的序列化器
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());

        // 设置value的序列化器，使用 GenericJackson2JsonRedisSerializer
        redisTemplate.setValueSerializer(genericJackson2JsonRedisSerializer);
        redisTemplate.setHashValueSerializer(genericJackson2JsonRedisSerializer);

        return redisTemplate;
    }
}
