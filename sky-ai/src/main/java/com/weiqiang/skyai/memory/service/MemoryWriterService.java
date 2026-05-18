package com.weiqiang.skyai.memory.service;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.Optional;
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
            User-managed facts may also be edited or deleted later from the UI, so only persist what the user explicitly states in this turn.
            Return an object with optional fact entries and optional corrections.
            Each fact field must be omitted when there is no new information, or include {"value": ..., "confidence": 0.0-1.0}.
            A fact entry value may be null to signal that the user wants the fact forgotten.
            Use these keys only: favorite_dishes, favorite_flavors, dietary_restrictions, default_address, operational_notes, corrections.
            
            Strict Response Requirement:
            Respond ONLY with the JSON object. Do not include any preface, markdown, or analysis.
            Your response MUST start with '{'.
            
            Example Output:
            {"favorite_dishes":{"value":["平菇豆腐汤"],"confidence":1.0},"favorite_flavors":{"value":"清淡","confidence":0.9},"corrections":["favorite_flavors"]}
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
        parse(stripJsonFences(content)).ifPresent(extraction -> {
            log.debug("extracted memory facts userId={} favorite_dishes.value={} favorite_dishes.confidence={} favorite_flavors.value={} favorite_flavors.confidence={} dietary_restrictions.value={} dietary_restrictions.confidence={} default_address.value={} default_address.confidence={} operational_notes.value={} operational_notes.confidence={} corrections={}",
                    userId,
                    valueOf(extraction.favoriteDishes()),
                    confidenceOf(extraction.favoriteDishes()),
                    valueOf(extraction.favoriteFlavors()),
                    confidenceOf(extraction.favoriteFlavors()),
                    valueOf(extraction.dietaryRestrictions()),
                    confidenceOf(extraction.dietaryRestrictions()),
                    valueOf(extraction.defaultAddress()),
                    confidenceOf(extraction.defaultAddress()),
                    valueOf(extraction.operationalNotes()),
                    confidenceOf(extraction.operationalNotes()),
                    extraction.corrections());
            merge(userId, extraction);
        });
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

    /**
     * Applies the structured extraction envelope, skipping absent fields, deleting explicit nulls, and honoring corrections.
     */
    private void merge(String userId, MemoryExtraction extraction) {
        List<String> corrections = extraction.corrections() == null ? List.of() : extraction.corrections();

        if (extraction.favoriteDishes() != null) {
            if (extraction.favoriteDishes().value() == null || extraction.favoriteDishes().value().isNull()) {
                userMemoryFactService.deleteFact(userId, MemoryFactKey.FAVORITE_DISHES);
            } else {
                userMemoryFactService.upsertFact(userId, MemoryFactKey.FAVORITE_DISHES,
                        toFactValue(extraction.favoriteDishes().value()), MemoryFactSourceType.USER,
                        extraction.favoriteDishes().confidence(), corrections.contains(MemoryFactKey.FAVORITE_DISHES.value()));
            }
        }
        if (extraction.favoriteFlavors() != null) {
            if (extraction.favoriteFlavors().value() == null || extraction.favoriteFlavors().value().isNull()) {
                userMemoryFactService.deleteFact(userId, MemoryFactKey.FAVORITE_FLAVORS);
            } else {
                userMemoryFactService.upsertFact(userId, MemoryFactKey.FAVORITE_FLAVORS,
                        toFactValue(extraction.favoriteFlavors().value()), MemoryFactSourceType.USER,
                        extraction.favoriteFlavors().confidence(), corrections.contains(MemoryFactKey.FAVORITE_FLAVORS.value()));
            }
        }
        if (extraction.dietaryRestrictions() != null) {
            if (extraction.dietaryRestrictions().value() == null || extraction.dietaryRestrictions().value().isNull()) {
                userMemoryFactService.deleteFact(userId, MemoryFactKey.DIETARY_RESTRICTIONS);
            } else {
                userMemoryFactService.upsertFact(userId, MemoryFactKey.DIETARY_RESTRICTIONS,
                        toFactValue(extraction.dietaryRestrictions().value()), MemoryFactSourceType.USER,
                        extraction.dietaryRestrictions().confidence(), corrections.contains(MemoryFactKey.DIETARY_RESTRICTIONS.value()));
            }
        }
        if (extraction.defaultAddress() != null) {
            if (extraction.defaultAddress().value() == null || extraction.defaultAddress().value().isNull()) {
                userMemoryFactService.deleteFact(userId, MemoryFactKey.DEFAULT_ADDRESS);
            } else {
                userMemoryFactService.upsertFact(userId, MemoryFactKey.DEFAULT_ADDRESS,
                        toFactValue(extraction.defaultAddress().value()), MemoryFactSourceType.USER,
                        extraction.defaultAddress().confidence(), corrections.contains(MemoryFactKey.DEFAULT_ADDRESS.value()));
            }
        }
        if (extraction.operationalNotes() != null) {
            if (extraction.operationalNotes().value() == null || extraction.operationalNotes().value().isNull()) {
                userMemoryFactService.deleteFact(userId, MemoryFactKey.OPERATIONAL_NOTES);
            } else {
                userMemoryFactService.upsertFact(userId, MemoryFactKey.OPERATIONAL_NOTES,
                        toFactValue(extraction.operationalNotes().value()), MemoryFactSourceType.USER,
                        extraction.operationalNotes().confidence(), corrections.contains(MemoryFactKey.OPERATIONAL_NOTES.value()));
            }
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

    /**
     * Parses the extraction envelope and returns empty when the model response contains no payload.
     */
    private Optional<MemoryExtraction> parse(String content) {
        if (!StringUtils.hasText(content)) {
            return Optional.empty();
        }
        try {
            String jsonMatch = content;
            int firstBrace = content.indexOf("{");
            int lastBrace = content.lastIndexOf("}");
            if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
                jsonMatch = content.substring(firstBrace, lastBrace + 1);
            }
            if ("{}".equals(jsonMatch.replaceAll("\\s+", ""))) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(jsonMatch, MemoryExtraction.class));
        } catch (Exception ex) {
            log.error("JSON 解析失败，原始响应内容为: \n{}", content);
            throw new IllegalStateException("Failed to parse memory extraction", ex);
        }
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

    private String valueOf(FactEntry factEntry) {
        if (factEntry == null || factEntry.value() == null || factEntry.value().isNull()) {
            return null;
        }
        JsonNode value = factEntry.value();
        return value.isValueNode() ? value.asText() : value.toString();
    }

    private Double confidenceOf(FactEntry factEntry) {
        return factEntry == null ? null : factEntry.confidence();
    }

    private String toFactValue(JsonNode value) {
        return value == null || value.isNull() ? null : (value.isValueNode() ? value.asText() : value.toString());
    }

    private String safeText(String text) {
        return StringUtils.hasText(text) ? text.trim() : "";
    }
}
