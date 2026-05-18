package com.weiqiang.skyai.memory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.weiqiang.skyai.memory.model.MemoryFactKey;
import com.weiqiang.skyai.memory.model.MemoryFactSourceType;
import com.weiqiang.skyai.memory.model.UserMemory;
import com.weiqiang.skyai.memory.model.UserMemoryFact;
import com.weiqiang.skyai.memory.model.UserMemoryFactHistory;
import com.weiqiang.skyai.memory.repository.UserMemoryFactHistoryRepository;
import com.weiqiang.skyai.memory.repository.UserMemoryFactRepository;
import com.weiqiang.skyai.memory.repository.UserMemoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.Function;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserMemoryFactService {

    private static final Comparator<UserMemoryFact> FACT_SORT_COMPARATOR = Comparator
            .comparingInt(UserMemoryFactService::sourcePriority)
            .thenComparing(fact -> Optional.ofNullable(fact.getConfidence()).orElse(0.0d), Comparator.reverseOrder())
            .thenComparing(UserMemoryFact::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(UserMemoryFact::getFactKey, Comparator.nullsLast(Comparator.naturalOrder()));

    private final UserMemoryFactRepository userMemoryFactRepository;
    private final UserMemoryFactHistoryRepository userMemoryFactHistoryRepository;
    private final UserMemoryRepository userMemoryRepository;

    /**
     * Returns the user's persisted memory facts in repository order.
     *
     * @param userId the user identifier
     * @return the user's memory facts or an empty list when the user id is blank
     */
    @Transactional(readOnly = true)
    public List<UserMemoryFact> findFacts(String userId) {
        return StringUtils.hasText(userId) ? userMemoryFactRepository.findAllByUserIdOrderByUpdatedAtDesc(userId) : List.of();
    }

    /**
     * Returns the user's facts sorted for display and prompt injection.
     *
     * @param userId the user identifier
     * @return sorted memory facts with manual facts first
     */
    @Transactional(readOnly = true)
    public List<UserMemoryFact> findFactsSorted(String userId) {
        if (!StringUtils.hasText(userId)) {
            return List.of();
        }
        return findFacts(userId).stream().sorted(FACT_SORT_COMPARATOR).toList();
    }

    /**
     * Returns a single fact for the given user and key.
     *
     * @param userId the user identifier
     * @param factKey the fact key
     * @return the matching fact if present
     */
    @Transactional(readOnly = true)
    public Optional<UserMemoryFact> findFact(String userId, MemoryFactKey factKey) {
        if (!StringUtils.hasText(userId) || factKey == null) {
            return Optional.empty();
        }
        return userMemoryFactRepository.findByUserIdAndFactKey(userId, factKey.value());
    }

    /**
     * Returns the audit history for a user and fact key.
     *
     * @param userId the user identifier
     * @param factKey the fact key
     * @return history rows ordered from newest to oldest
     */
    @Transactional(readOnly = true)
    public List<UserMemoryFactHistory> findHistory(String userId, MemoryFactKey factKey) {
        if (!StringUtils.hasText(userId) || factKey == null) {
            return List.of();
        }
        return userMemoryFactHistoryRepository.findAllByUserIdAndFactKeyOrderByChangedAtDesc(userId, factKey.value());
    }

    /**
     * Builds a dietary preference summary for the LLM context.
     *
     * @param userId the user identifier
     * @return a human-readable summary or the cached fallback value
     */
    @Transactional(readOnly = true)
    public String dietaryPreferencesSummary(String userId) {
        return summarize(userId,
                MemoryFactKey.FAVORITE_DISHES, "喜欢的菜",
                MemoryFactKey.FAVORITE_FLAVORS, "口味偏好",
                MemoryFactKey.DIETARY_RESTRICTIONS, "饮食限制",
                UserMemory::getDietaryPrefs);
    }

    /**
     * Returns the default address summary for prompt injection.
     *
     * @param userId the user identifier
     * @return the best available address summary
     */
    @Transactional(readOnly = true)
    public String defaultAddressSummary(String userId) {
        return summarizeSingle(userId, MemoryFactKey.DEFAULT_ADDRESS, UserMemory::getDefaultAddress);
    }

    /**
     * Returns the operational notes summary for prompt injection.
     *
     * @param userId the user identifier
     * @return the best available operational notes summary
     */
    @Transactional(readOnly = true)
    public String operationalNotesSummary(String userId) {
        List<String> notes = factValues(userId, MemoryFactKey.OPERATIONAL_NOTES);
        if (!notes.isEmpty()) {
            return oneSentence(String.join("；", notes));
        }
        return fallback(userId, UserMemory::getKnownIssues);
    }

    /**
     * Returns the detailed operational notes text without sentence truncation.
     *
     * @param userId the user identifier
     * @return the detailed operational notes
     */
    @Transactional(readOnly = true)
    public String operationalNotesDetailed(String userId) {
        List<String> notes = factValues(userId, MemoryFactKey.OPERATIONAL_NOTES);
        if (!notes.isEmpty()) {
            return String.join("；", notes);
        }
        return fallback(userId, UserMemory::getKnownIssues);
    }

    /**
     * Returns the user profile notes summary for prompt injection.
     *
     * @param userId the user identifier
     * @return the best available summary of the user's profile notes
     */
    @Transactional(readOnly = true)
    public String userProfileNotesSummary(String userId) {
        List<String> notes = factValues(userId, MemoryFactKey.USER_PROFILE_NOTES);
        if (notes.isEmpty()) {
            return null;
        }
        return limit(oneSentence(normalizeDisplayText(notes.get(0))), 120);
    }

    /**
     * Returns the user profile notes detailed text for prompt injection.
     *
     * @param userId the user identifier
     * @return the detailed user profile notes
     */
    @Transactional(readOnly = true)
    public String userProfileNotesDetailed(String userId) {
        List<String> notes = factValues(userId, MemoryFactKey.USER_PROFILE_NOTES);
        if (notes.isEmpty()) {
            return null;
        }
        return limit(normalizeDisplayText(notes.get(0)), 500);
    }

    /**
     * Upserts a memory fact with string storage.
     *
     * @param userId the user identifier
     * @param factKey the fact key
     * @param factValue the serialized fact value
     * @param sourceType the fact source
     * @param confidence the confidence score
     */
    @Transactional
    public void upsertFact(String userId, MemoryFactKey factKey, String factValue, MemoryFactSourceType sourceType, Double confidence) {
        upsertFactInternal(userId, factKey, factValue, sourceType, confidence, false);
    }

    /**
     * Upserts a memory fact with string storage and optional overwrite history.
     *
     * @param userId the user identifier
     * @param factKey the fact key
     * @param factValue the serialized fact value
     * @param sourceType the fact source
     * @param confidence the confidence score
     * @param corrected whether the overwrite should be archived
     */
    @Transactional
    public void upsertFact(String userId, MemoryFactKey factKey, String factValue, MemoryFactSourceType sourceType, Double confidence, boolean corrected) {
        upsertFactInternal(userId, factKey, factValue, sourceType, confidence, corrected);
    }

    /**
     * Upserts a memory fact from JSON without forcing overwrite history.
     *
     * @param userId the user identifier
     * @param factKey the fact key
     * @param factValue the JSON payload from the API
     * @param sourceType the fact source
     * @param confidence the confidence score
     */
    @Transactional
    public void upsertFact(String userId, MemoryFactKey factKey, JsonNode factValue, MemoryFactSourceType sourceType, Double confidence) {
        upsertFactInternal(userId, factKey, normalizeJsonValue(factValue), sourceType, confidence, false);
    }

    /**
     * Upserts a memory fact from JSON and optionally archives the previous value.
     *
     * @param userId the user identifier
     * @param factKey the fact key
     * @param factValue the JSON payload from the API
     * @param sourceType the fact source
     * @param confidence the confidence score
     * @param writeHistoryOnOverwrite whether the existing value should be archived
     */
    @Transactional
    public void upsertFact(String userId, MemoryFactKey factKey, JsonNode factValue, MemoryFactSourceType sourceType, Double confidence, boolean writeHistoryOnOverwrite) {
        upsertFactInternal(userId, factKey, normalizeJsonValue(factValue), sourceType, confidence, writeHistoryOnOverwrite);
    }

    /**
     * Deletes a fact and refreshes the derived user memory row.
     *
     * @param userId the user identifier
     * @param factKey the fact key
     */
    @Transactional
    public void deleteFact(String userId, MemoryFactKey factKey) {
        if (!StringUtils.hasText(userId) || factKey == null) {
            return;
        }
        userMemoryFactRepository.findByUserIdAndFactKey(userId, factKey.value()).ifPresent(existingFact -> {
            Instant now = Instant.now();
            UserMemoryFactHistory history = new UserMemoryFactHistory();
            history.setUserId(userId);
            history.setFactKey(factKey.value());
            history.setOldValue(existingFact.getFactValue());
            history.setNewValue(null);
            history.setSourceType(existingFact.getSourceType() == null ? MemoryFactSourceType.INFERRED : existingFact.getSourceType());
            history.setConfidence(existingFact.getConfidence());
            history.setChangedAt(now);
            userMemoryFactHistoryRepository.save(history);
            userMemoryFactRepository.delete(existingFact);
        });
        refreshUserMemorySummary(userId);
    }

    /**
     * Refreshes the derived `user_memory` row from the persisted facts.
     *
     * @param userId the user identifier
     */
    @Transactional
    public void refreshUserMemorySummary(String userId) {
        if (!StringUtils.hasText(userId)) {
            return;
        }
        List<UserMemoryFact> facts = findFacts(userId);
        UserMemory userMemory = userMemoryRepository.findById(userId).orElseGet(UserMemory::new);
        userMemory.setUserId(userId);
        userMemory.setDietaryPrefs(buildDietaryPreferencesSummary(facts));
        userMemory.setDefaultAddress(latestFactValue(facts, MemoryFactKey.DEFAULT_ADDRESS));
        userMemory.setKnownIssues(limit(buildOperationalNotesSummary(facts)));
        userMemory.setUpdatedAt(Instant.now());
        userMemoryRepository.save(userMemory);
    }

    /**
     * Builds a prompt-ready memory block for the provided fact keys.
     *
     * @param userId the user identifier
     * @param factKeys the keys to include
     * @return the formatted memory block or an empty string when no matching facts exist
     */
    @Transactional(readOnly = true)
    public String buildMemoryBlock(String userId, Collection<MemoryFactKey> factKeys) {
        if (!StringUtils.hasText(userId) || factKeys == null || factKeys.isEmpty()) {
            return "";
        }
        List<String> lines = findFactsSorted(userId).stream()
                .filter(fact -> factKeys.stream().anyMatch(key -> key.value().equals(fact.getFactKey())))
                .map(this::formatMemoryLine)
                .filter(StringUtils::hasText)
                .toList();
        if (lines.isEmpty()) {
            return "";
        }
        return "Relevant memory:\n" + String.join("\n", lines);
    }

    private String summarize(String userId, MemoryFactKey firstKey, String firstLabel,
                             MemoryFactKey secondKey, String secondLabel,
                             MemoryFactKey thirdKey, String thirdLabel,
                             Function<UserMemory, String> fallbackExtractor) {
        List<String> parts = new ArrayList<>();
        appendFact(parts, userId, firstKey, firstLabel);
        appendFact(parts, userId, secondKey, secondLabel);
        appendFact(parts, userId, thirdKey, thirdLabel);
        if (!parts.isEmpty()) {
            return oneSentence(String.join("；", parts));
        }
        return fallback(userId, fallbackExtractor);
    }

    private String summarizeSingle(String userId, MemoryFactKey factKey, Function<UserMemory, String> fallbackExtractor) {
        List<String> values = factValues(userId, factKey);
        if (!values.isEmpty()) {
            return oneSentence(values.get(0));
        }
        return fallback(userId, fallbackExtractor);
    }

    private void appendFact(List<String> parts, String userId, MemoryFactKey factKey, String label) {
        if (factKey == null) {
            return;
        }
        factValues(userId, factKey).stream().findFirst().ifPresent(value -> parts.add(label + "：" + value));
    }

    private List<String> factValues(String userId, MemoryFactKey factKey) {
        if (!StringUtils.hasText(userId) || factKey == null) {
            return List.of();
        }
        return findFacts(userId).stream()
                .filter(fact -> factKey.value().equals(fact.getFactKey()))
                .map(UserMemoryFact::getFactValue)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private String latestFactValue(List<UserMemoryFact> facts, MemoryFactKey factKey) {
        return facts.stream()
                .filter(fact -> factKey.value().equals(fact.getFactKey()))
                .sorted(Comparator.comparing(UserMemoryFact::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .map(UserMemoryFact::getFactValue)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private String buildDietaryPreferencesSummary(List<UserMemoryFact> facts) {
        StringJoiner joiner = new StringJoiner("；");
        addFactValue(joiner, facts, MemoryFactKey.FAVORITE_DISHES, "喜欢的菜");
        addFactValue(joiner, facts, MemoryFactKey.FAVORITE_FLAVORS, "口味偏好");
        addFactValue(joiner, facts, MemoryFactKey.DIETARY_RESTRICTIONS, "饮食限制");
        return joiner.length() == 0 ? null : joiner.toString();
    }

    private String buildOperationalNotesSummary(List<UserMemoryFact> facts) {
        return facts.stream()
                .filter(fact -> MemoryFactKey.OPERATIONAL_NOTES.value().equals(fact.getFactKey()))
                .map(UserMemoryFact::getFactValue)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .limit(3)
                .reduce((left, right) -> left + "；" + right)
                .orElse(null);
    }

    private void addFactValue(StringJoiner joiner, List<UserMemoryFact> facts, MemoryFactKey factKey, String label) {
        Optional.ofNullable(latestFactValue(facts, factKey))
                .filter(StringUtils::hasText)
                .ifPresent(value -> joiner.add(label + "：" + value));
    }

    private String fallback(String userId, Function<UserMemory, String> extractor) {
        if (!StringUtils.hasText(userId) || extractor == null) {
            return null;
        }
        return userMemoryRepository.findById(userId)
                .map(extractor)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .orElse(null);
    }

    private String oneSentence(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String value = text.trim();
        int sentenceEnd = value.indexOf('。');
        if (sentenceEnd >= 0) {
            return value.substring(0, sentenceEnd + 1).trim();
        }
        sentenceEnd = value.indexOf('.');
        return sentenceEnd >= 0 ? value.substring(0, sentenceEnd + 1).trim() : value;
    }

    private String limit(String text) {
        return limit(text, 500);
    }

    private String limit(String text, int maxLength) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String value = text.trim();
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String normalizeDisplayText(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    private void upsertFactInternal(String userId, MemoryFactKey factKey, String factValue, MemoryFactSourceType sourceType, Double confidence, boolean writeHistoryOnOverwrite) {
        if (!StringUtils.hasText(userId) || factKey == null || !StringUtils.hasText(factValue)) {
            return;
        }
        if (confidence != null && confidence < 0.7) {
            log.debug("skip memory fact upsert for userId={}, factKey={}, confidence={}", userId, factKey.value(), confidence);
            return;
        }
        Double effectiveConfidence = confidence == null ? 1.0 : confidence;
        Instant now = Instant.now();
        Optional<UserMemoryFact> existingFact = userMemoryFactRepository.findByUserIdAndFactKey(userId, factKey.value());
        UserMemoryFact fact = existingFact.orElseGet(UserMemoryFact::new);
        String mergedValue = mergeValue(factKey, existingFact.map(UserMemoryFact::getFactValue).orElse(null), factValue);
        if (existingFact.isPresent() && writeHistoryOnOverwrite) {
            UserMemoryFactHistory history = new UserMemoryFactHistory();
            history.setUserId(userId);
            history.setFactKey(factKey.value());
            history.setOldValue(existingFact.get().getFactValue());
            history.setNewValue(mergedValue);
            history.setSourceType(sourceType == null ? MemoryFactSourceType.INFERRED : sourceType);
            history.setConfidence(effectiveConfidence);
            history.setChangedAt(now);
            userMemoryFactHistoryRepository.save(history);
        }
        fact.setUserId(userId);
        fact.setFactKey(factKey.value());
        if (existingFact.isEmpty()) {
            fact.setCreatedAt(now);
        }
        fact.setFactValue(mergedValue);
        fact.setSourceType(sourceType == null ? MemoryFactSourceType.INFERRED : sourceType);
        fact.setConfidence(effectiveConfidence);
        fact.setUpdatedAt(now);
        userMemoryFactRepository.save(fact);
        refreshUserMemorySummary(userId);
    }

    private String normalizeJsonValue(JsonNode factValue) {
        if (factValue == null || factValue.isNull()) {
            return null;
        }
        if (factValue.isTextual() || factValue.isNumber() || factValue.isBoolean()) {
            return factValue.asText();
        }
        return factValue.toString();
    }

    private String formatMemoryLine(UserMemoryFact fact) {
        if (fact == null || !StringUtils.hasText(fact.getFactValue())) {
            return "";
        }
        String label = memoryLabel(fact.getFactKey());
        String value = fact.getFactValue().trim();
        String suffix = fact.getSourceType() == MemoryFactSourceType.USER_MANUAL ? " [用户自设]" : "";
        return "- " + label + ": " + value + suffix;
    }

    private String memoryLabel(String factKey) {
        if (MemoryFactKey.FAVORITE_DISHES.value().equals(factKey)) {
            return "Favorite dishes";
        }
        if (MemoryFactKey.FAVORITE_FLAVORS.value().equals(factKey)) {
            return "Favorite flavors";
        }
        if (MemoryFactKey.DIETARY_RESTRICTIONS.value().equals(factKey)) {
            return "Dietary restrictions";
        }
        if (MemoryFactKey.DEFAULT_ADDRESS.value().equals(factKey)) {
            return "Default address";
        }
        if (MemoryFactKey.OPERATIONAL_NOTES.value().equals(factKey)) {
            return "Operational notes";
        }
        if (MemoryFactKey.USER_PROFILE_NOTES.value().equals(factKey)) {
            return "User profile notes";
        }
        return factKey;
    }

    private static int sourcePriority(UserMemoryFact fact) {
        if (fact == null || fact.getSourceType() == null) {
            return Integer.MAX_VALUE;
        }
        return fact.getSourceType().sortPriority();
    }

    private String mergeValue(MemoryFactKey factKey, String existing, String next) {
        String nextValue = next == null ? null : next.trim();
        if (!StringUtils.hasText(nextValue)) {
            return existing;
        }
        if (factKey == MemoryFactKey.OPERATIONAL_NOTES && StringUtils.hasText(existing)) {
            String merged = existing.trim() + "；" + nextValue;
            return merged.length() <= 1000 ? merged : merged.substring(0, 1000);
        }
        return nextValue;
    }
}
