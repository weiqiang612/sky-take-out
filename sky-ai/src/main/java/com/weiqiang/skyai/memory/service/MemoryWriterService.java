package com.weiqiang.skyai.memory.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiqiang.skyai.intent_recognition.model.ConfidenceLevel;
import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionResult;
import com.weiqiang.skyai.intent_recognition.model.IntentType;
import com.weiqiang.skyai.memory.model.MemoryFactKey;
import com.weiqiang.skyai.memory.model.MemoryFactSourceType;
import com.weiqiang.skyai.memory.repository.RedisChatMemoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryWriterService {

    private static final String EXTRACTION_SYSTEM_PROMPT = """
            Return JSON only. No markdown, no code fences, no preamble.
            Extract stable user memory facts from the user's explicit statements only.
            Ignore assistant replies and raw tool payloads; confirmed tool outcomes are persisted separately.
            Use these keys only: favorite_dishes, favorite_flavors, dietary_restrictions, default_address, operational_notes.
            If a field is not present, return null.
            
            Strict Response Requirement:
            Respond ONLY with the JSON object. Do not include any preface, markdown, or analysis.
            Your response MUST start with '{'.
            
            Example Output:
            {"favorite_dishes":"平菇豆腐汤","favorite_flavors":"清淡","dietary_restrictions":null,"default_address":null,"operational_notes":null}
            """;

    private static final Pattern ORDER_ID_PATTERN = Pattern.compile("order\\s*(?:id)?\\s*[:：]?\\s*([0-9]+)", Pattern.CASE_INSENSITIVE);

    private final ChatClient.Builder chatClientBuilder;
    private final RedisChatMemoryRepository redisChatMemoryRepository;
    private final UserMemoryFactService userMemoryFactService;
    private final ObjectMapper objectMapper;

    @Async
    public void writeTurn(String userId, String conversationId, IntentRecognitionResult intentResult) {
        List<Message> messages = redisChatMemoryRepository.findByConversationId(conversationId);
        redisChatMemoryRepository.saveAll(conversationId, messages);
        if (intentResult != null && intentResult.requiresHumanConfirmation()) {
            return;
        }
        persistToolOutcomes(userId, messages, intentResult);
        if (intentResult == null || shouldSkipLongTermWrite(intentResult)) {
            log.debug("skip long-term memory write for userId={}, intent={}, confidence={}", userId,
                    intentResult == null ? null : intentResult.intent(),
                    intentResult == null ? null : intentResult.confidence());
            return;
        }
        String transcript = buildUserTranscript(messages);
        if (!StringUtils.hasText(transcript)) {
            return;
        }
        String content = chatClientBuilder.build().prompt()
                .system(EXTRACTION_SYSTEM_PROMPT)
                .user(transcript)
                .call()
                .content();
        MemoryExtraction extraction = parse(stripJsonFences(content));
        log.debug("extracted memory facts for userId={}: favoriteDishes={}, favoriteFlavors={}, dietaryRestrictions={}, defaultAddress={}, operationalNotes={}",
                userId,
                extraction.favoriteDishes(),
                extraction.favoriteFlavors(),
                extraction.dietaryRestrictions(),
                extraction.defaultAddress(),
                extraction.operationalNotes());
        merge(userId, extraction);
    }

    private boolean shouldSkipLongTermWrite(IntentRecognitionResult intentResult) {
        return (intentResult.intent() == IntentType.FAQ || intentResult.intent() == IntentType.OTHER)
                && intentResult.confidence() == ConfidenceLevel.LOW;
    }

    private void persistToolOutcomes(String userId, List<Message> messages, IntentRecognitionResult intentResult) {
        if (intentResult == null) {
            return;
        }
        for (Message message : messages) {
            if (message instanceof ToolResponseMessage toolMessage) {
                for (ToolResponseMessage.ToolResponse response : toolMessage.getResponses()) {
                    persistToolOutcome(userId, intentResult.intent(), response.responseData());
                }
            }
        }
    }

    private void persistToolOutcome(String userId, IntentType intent, String responseData) {
        if (!StringUtils.hasText(responseData) || responseData.startsWith("FAIL:")) {
            return;
        }
        if (intent == IntentType.CANCEL_ORDER) {
            persistOperationalNote(userId, "Cancelled order " + extractOrderId(responseData) + " on " + today());
            return;
        }
        if (intent == IntentType.REQUEST_REFUND) {
            String reason = extractTail(responseData);
            persistOperationalNote(userId, "Refund issued for order " + extractOrderId(responseData)
                    + (StringUtils.hasText(reason) ? ": " + reason : ""));
            return;
        }
        if (intent == IntentType.CHANGE_ADDRESS) {
            userMemoryFactService.upsertFact(userId, MemoryFactKey.DEFAULT_ADDRESS, extractTail(responseData), MemoryFactSourceType.TOOL, null);
        }
    }

    private void persistOperationalNote(String userId, String note) {
        if (StringUtils.hasText(note)) {
            userMemoryFactService.upsertFact(userId, MemoryFactKey.OPERATIONAL_NOTES, note, MemoryFactSourceType.TOOL, null);
        }
    }

    private void merge(String userId, MemoryExtraction extraction) {
        if (StringUtils.hasText(extraction.favoriteDishes())) {
            userMemoryFactService.upsertFact(userId, MemoryFactKey.FAVORITE_DISHES, extraction.favoriteDishes(), MemoryFactSourceType.USER, extraction.confidence());
        }
        if (StringUtils.hasText(extraction.favoriteFlavors())) {
            userMemoryFactService.upsertFact(userId, MemoryFactKey.FAVORITE_FLAVORS, extraction.favoriteFlavors(), MemoryFactSourceType.USER, extraction.confidence());
        }
        if (StringUtils.hasText(extraction.dietaryRestrictions())) {
            userMemoryFactService.upsertFact(userId, MemoryFactKey.DIETARY_RESTRICTIONS, extraction.dietaryRestrictions(), MemoryFactSourceType.USER, extraction.confidence());
        }
        if (StringUtils.hasText(extraction.defaultAddress())) {
            userMemoryFactService.upsertFact(userId, MemoryFactKey.DEFAULT_ADDRESS, extraction.defaultAddress(), MemoryFactSourceType.USER, extraction.confidence());
        }
        if (StringUtils.hasText(extraction.operationalNotes())) {
            userMemoryFactService.upsertFact(userId, MemoryFactKey.OPERATIONAL_NOTES, extraction.operationalNotes(), MemoryFactSourceType.USER, extraction.confidence());
        }
    }

    private String buildUserTranscript(List<Message> messages) {
        return messages.stream()
                .filter(message -> message.getMessageType() == MessageType.USER)
                .map(Message::getText)
                .map(this::safeText)
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
        if (content == null || content.isBlank()) {
            return new MemoryExtraction("", "", "", "", "", null);
        }

        try {
            // 健壮性处理：提取 JSON 部分，忽略前后的废话
            String jsonMatch = content;
            int firstBrace = content.indexOf("{");
            int lastBrace = content.lastIndexOf("}");

            if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
                jsonMatch = content.substring(firstBrace, lastBrace + 1);
            }

            return objectMapper.readValue(jsonMatch, MemoryExtraction.class);
        } catch (Exception ex) {
            // 打印出导致失败的原始内容，方便排查
            log.error("JSON 解析失败，原始响应内容为: \n{}", content);
            throw new IllegalStateException("Failed to parse memory extraction", ex);
        }
    }

    private String safeText(String text) {
        return StringUtils.hasText(text) ? text.trim() : "";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MemoryExtraction(
            @JsonProperty("favorite_dishes") String favoriteDishes,
            @JsonProperty("favorite_flavors") String favoriteFlavors,
            @JsonProperty("dietary_restrictions") String dietaryRestrictions,
            @JsonProperty("default_address") String defaultAddress,
            @JsonProperty("operational_notes") String operationalNotes,
            @JsonProperty("confidence") Double confidence) {
    }

    private String extractOrderId(String responseData) {
        Matcher matcher = ORDER_ID_PATTERN.matcher(responseData);
        return matcher.find() ? matcher.group(1) : "unknown";
    }

    private String extractTail(String responseData) {
        int colon = responseData.indexOf(':');
        return colon >= 0 && colon + 1 < responseData.length() ? responseData.substring(colon + 1).trim() : "";
    }

    private String today() {
        return LocalDate.now(ZoneId.systemDefault()).toString();
    }
}
