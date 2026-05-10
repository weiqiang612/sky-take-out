package com.weiqiang.skyai.memory.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.Set;

@Repository
public class RedisChatMemoryRepository implements ChatMemoryRepository {

    private static final String KEY_PREFIX = "chat:";
    private static final Duration TTL = Duration.ofHours(2);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisChatMemoryRepository(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        String value = redisTemplate.opsForValue().get(key(conversationId));
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        try {
            List<StoredMessage> storedMessages = objectMapper.readValue(value, new TypeReference<>() {
            });
            return storedMessages.stream().map(this::toMessage).toList();
        } catch (Exception ex) {
            return List.of();
        }
    }

    @Override
    public List<String> findConversationIds() {
        Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
        if (keys == null) {
            return List.of();
        }
        return keys.stream().map(key -> key.substring(KEY_PREFIX.length())).toList();
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        try {
            List<StoredMessage> storedMessages = messages.stream().map(StoredMessage::from).toList();
            redisTemplate.opsForValue().set(key(conversationId), objectMapper.writeValueAsString(storedMessages), TTL);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to save chat memory", ex);
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        redisTemplate.delete(key(conversationId));
    }

    private String key(String conversationId) {
        return KEY_PREFIX + conversationId;
    }

    private Message toMessage(StoredMessage storedMessage) {
        if (storedMessage.messageType() == MessageType.SYSTEM) {
            return new SystemMessage(storedMessage.text());
        }
        if (storedMessage.messageType() == MessageType.ASSISTANT) {
            return new AssistantMessage(storedMessage.text());
        }
        return new UserMessage(storedMessage.text());
    }

    private record StoredMessage(MessageType messageType, String text) {

        static StoredMessage from(Message message) {
            return new StoredMessage(message.getMessageType(), message.getText());
        }
    }
}
