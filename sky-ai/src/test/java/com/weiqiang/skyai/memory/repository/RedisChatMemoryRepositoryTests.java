package com.weiqiang.skyai.memory.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisChatMemoryRepositoryTests {

    @Test
    void saveAllRefreshesTtl() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        RedisChatMemoryRepository repository = new RedisChatMemoryRepository(redisTemplate, new ObjectMapper());
        repository.saveAll("abc", List.of());

        verify(valueOperations).set("chat:abc", "[]", Duration.ofHours(2));
    }

    @Test
    void findByConversationIdReturnsMessages() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("chat:abc")).thenReturn("""
                [{"messageType":"USER","text":"hello"},{"messageType":"ASSISTANT","text":"hi"}]
                """);

        RedisChatMemoryRepository repository = new RedisChatMemoryRepository(redisTemplate, new ObjectMapper());
        List<Message> messages = repository.findByConversationId("abc");

        assertEquals(2, messages.size());
        assertEquals("hello", messages.get(0).getText());
        assertEquals("hi", messages.get(1).getText());
    }

    @Test
    void findByConversationIdReturnsEmptyListForMissingKey() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        RedisChatMemoryRepository repository = new RedisChatMemoryRepository(redisTemplate, new ObjectMapper());

        assertTrue(repository.findByConversationId("missing").isEmpty());
    }
}
