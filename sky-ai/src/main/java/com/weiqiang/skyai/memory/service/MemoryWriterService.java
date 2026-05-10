package com.weiqiang.skyai.memory.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiqiang.skyai.intent_recognition.model.ConfidenceLevel;
import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionResult;
import com.weiqiang.skyai.intent_recognition.model.IntentType;
import com.weiqiang.skyai.memory.model.UserMemory;
import com.weiqiang.skyai.memory.repository.RedisChatMemoryRepository;
import com.weiqiang.skyai.memory.repository.UserMemoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryWriterService {

    private static final String EXTRACTION_SYSTEM_PROMPT = """
            Return JSON only. No markdown, no code fences, no preamble.
            Extract stable user memory facts from the conversation transcript.
            Use these keys only: dietary_prefs, default_address, known_issues.
            If a field is not present, return null.
            
            Strict Response Requirement:
            Respond ONLY with the JSON object. Do not include any preface, markdown, or analysis.
            Your response MUST start with '{'.
            
            Example Output:
            {"dietary_prefs":"喜欢吃辣","default_address":null,"known_issues":null}
            """;

    private final ChatClient.Builder chatClientBuilder;
    private final RedisChatMemoryRepository redisChatMemoryRepository;
    private final UserMemoryRepository userMemoryRepository;
    private final ObjectMapper objectMapper;

    @Async
    public void writeTurn(String userId, String conversationId, IntentRecognitionResult intentResult) {
        List<Message> messages = redisChatMemoryRepository.findByConversationId(conversationId);
        redisChatMemoryRepository.saveAll(conversationId, messages);
        // 需要人工确认的意图不写入长期记忆，避免误写入
        if (intentResult != null && intentResult.requiresHumanConfirmation()) {
            return;
        }
        // 对于 FAQ 和 OTHER 意图，如果置信度较低，也不写入长期记忆，避免误写入
        if (intentResult == null || shouldSkipLongTermWrite(intentResult)) {
            log.debug("skip long-term memory write for userId={}, intent={}, confidence={}", userId,
                    intentResult == null ? null : intentResult.intent(),
                    intentResult == null ? null : intentResult.confidence());
            return;
        }
        String transcript = buildTranscript(messages);
        if (!StringUtils.hasText(transcript)) {
            return;
        }
        // 提取用户的长期记忆信息，更新到数据库中
        String content = chatClientBuilder.build().prompt()
                .system(EXTRACTION_SYSTEM_PROMPT)
                .user(transcript)
                .call()
                .content();
        MemoryExtraction extraction = parse(stripJsonFences(content));
        log.debug("extracted memory facts for userId={}: dietaryPrefs={}, defaultAddress={}, knownIssues={}",
                userId, extraction.dietaryPrefs(), extraction.defaultAddress(), extraction.knownIssues());
        UserMemory userMemory = userMemoryRepository.findById(userId).orElseGet(UserMemory::new);
        userMemory.setUserId(userId);
        merge(userMemory, extraction);
        userMemory.setUpdatedAt(Instant.now());
        userMemoryRepository.save(userMemory);
    }

    private boolean shouldSkipLongTermWrite(IntentRecognitionResult intentResult) {
        return (intentResult.intent() == IntentType.FAQ || intentResult.intent() == IntentType.OTHER)
                && intentResult.confidence() == ConfidenceLevel.LOW;
    }

    // 构建对话转录文本，格式为 "SENDER: message text"，每条消息占一行
    private String buildTranscript(List<Message> messages) {
        return messages.stream()
                .map(message -> message.getMessageType() + ": " + safeText(message.getText()))
                .filter(StringUtils::hasText)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private String stripJsonFences(String content) {
        if (!StringUtils.hasText(content)) {
            return content;
        }
        return content.replaceAll("(?s)^```json\\s*", "")
                .replaceAll("(?s)^```\\s*", "")
                .replaceAll("(?s)\\s*```$", "")
                .trim();
    }

    private MemoryExtraction parse(String content) {
        try {
            return objectMapper.readValue(content, MemoryExtraction.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse memory extraction", ex);
        }
    }

    // 对于饮食偏好和默认地址，直接覆盖现有值；对于已知问题，追加到现有值后面，并限制总长度不超过500字符
    private void merge(UserMemory userMemory, MemoryExtraction extraction) {
        if (StringUtils.hasText(extraction.dietaryPrefs())) {
            userMemory.setDietaryPrefs(extraction.dietaryPrefs().trim());
        }
        if (StringUtils.hasText(extraction.defaultAddress())) {
            userMemory.setDefaultAddress(extraction.defaultAddress().trim());
        }
        if (StringUtils.hasText(extraction.knownIssues())) {
            userMemory.setKnownIssues(limit(userMemory.getKnownIssues(), extraction.knownIssues()));
        }
    }

    private String limit(String existing, String next) {
        String merged = StringUtils.hasText(existing) ? existing.trim() + " " + next.trim() : next.trim();
        return merged.length() <= 500 ? merged : merged.substring(0, 500);
    }

    private String safeText(String text) {
        return StringUtils.hasText(text) ? text.trim() : "";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MemoryExtraction(
            @JsonProperty("dietary_prefs") String dietaryPrefs,
            @JsonProperty("default_address") String defaultAddress,
            @JsonProperty("known_issues") String knownIssues) {
    }
}
