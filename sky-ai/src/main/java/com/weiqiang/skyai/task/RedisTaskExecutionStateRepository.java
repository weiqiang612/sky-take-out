package com.weiqiang.skyai.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiqiang.skyai.task.model.TaskExecutionState;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Repository
public class RedisTaskExecutionStateRepository implements TaskExecutionStateRepository {

    private static final String KEY_PREFIX = "task-plan:";
    private static final Duration TTL = Duration.ofHours(2);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisTaskExecutionStateRepository(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(TaskExecutionState state) {
        if (state == null || !StringUtils.hasText(state.conversationId())) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key(state.conversationId()), objectMapper.writeValueAsString(state), TTL);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to persist task execution state", ex);
        }
    }

    @Override
    public TaskExecutionState findByConversationId(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return null;
        }
        String value = redisTemplate.opsForValue().get(key(conversationId));
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return objectMapper.readValue(value, TaskExecutionState.class);
        } catch (Exception ex) {
            return null;
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return;
        }
        redisTemplate.delete(key(conversationId));
    }

    private String key(String conversationId) {
        return KEY_PREFIX + conversationId;
    }
}
