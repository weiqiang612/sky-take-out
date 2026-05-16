package com.weiqiang.skyai.memory.service;

import com.weiqiang.skyai.memory.model.MemoryFactKey;
import com.weiqiang.skyai.memory.model.MemoryFactSourceType;
import com.weiqiang.skyai.memory.model.UserMemory;
import com.weiqiang.skyai.memory.model.UserMemoryFact;
import com.weiqiang.skyai.memory.model.UserMemoryFactHistory;
import com.weiqiang.skyai.memory.repository.UserMemoryFactRepository;
import com.weiqiang.skyai.memory.repository.UserMemoryFactHistoryRepository;
import com.weiqiang.skyai.memory.repository.UserMemoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.Function;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserMemoryFactService {

    private final UserMemoryFactRepository userMemoryFactRepository;
    private final UserMemoryFactHistoryRepository userMemoryFactHistoryRepository;
    private final UserMemoryRepository userMemoryRepository;

    public List<UserMemoryFact> findFacts(String userId) {
        return StringUtils.hasText(userId) ? userMemoryFactRepository.findAllByUserIdOrderByUpdatedAtDesc(userId) : List.of();
    }

    public String dietaryPreferencesSummary(String userId) {
        return summarize(userId,
                MemoryFactKey.FAVORITE_DISHES, "喜欢的菜",
                MemoryFactKey.FAVORITE_FLAVORS, "口味偏好",
                MemoryFactKey.DIETARY_RESTRICTIONS, "饮食限制",
                UserMemory::getDietaryPrefs);
    }

    public String defaultAddressSummary(String userId) {
        return summarizeSingle(userId, MemoryFactKey.DEFAULT_ADDRESS, UserMemory::getDefaultAddress);
    }

    public String operationalNotesSummary(String userId) {
        List<String> notes = factValues(userId, MemoryFactKey.OPERATIONAL_NOTES);
        if (!notes.isEmpty()) {
            return oneSentence(String.join("；", notes));
        }
        return fallback(userId, UserMemory::getKnownIssues);
    }

    public String operationalNotesDetailed(String userId) {
        List<String> notes = factValues(userId, MemoryFactKey.OPERATIONAL_NOTES);
        if (!notes.isEmpty()) {
            return String.join("；", notes);
        }
        return fallback(userId, UserMemory::getKnownIssues);
    }

    /**
     * Upserts a memory fact with created_at tracking, correction history, and a confidence floor.
     */
    /*
     * Flyway migration:
     * alter table user_memory_fact add column if not exists created_at timestamp not null default now();
     * create table if not exists user_memory_fact_history (
     *     user_id varchar(255) not null,
     *     fact_key varchar(128) not null,
     *     old_value text,
     *     new_value text,
     *     source_type varchar(32) not null,
     *     confidence double precision null,
     *     changed_at timestamp not null,
     *     primary key (user_id, fact_key, changed_at)
     * );
     */
    public void upsertFact(String userId, MemoryFactKey factKey, String factValue, MemoryFactSourceType sourceType, Double confidence) {
        upsertFact(userId, factKey, factValue, sourceType, confidence, false);
    }

    /**
     * Upserts a memory fact and records the previous value when the caller marks the field as a correction.
     */
    public void upsertFact(String userId, MemoryFactKey factKey, String factValue, MemoryFactSourceType sourceType, Double confidence, boolean corrected) {
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
        if (corrected && existingFact.isPresent() && StringUtils.hasText(existingFact.get().getFactValue())) {
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

    /**
     * Deletes a fact and refreshes the summary so derived user memory stays in sync.
     */
    public void deleteFact(String userId, MemoryFactKey factKey) {
        if (!StringUtils.hasText(userId) || factKey == null) {
            return;
        }
        userMemoryFactRepository.findByUserIdAndFactKey(userId, factKey.value()).ifPresent(userMemoryFactRepository::delete);
        refreshUserMemorySummary(userId);
    }

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
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String value = text.trim();
        return value.length() <= 500 ? value : value.substring(0, 500);
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
