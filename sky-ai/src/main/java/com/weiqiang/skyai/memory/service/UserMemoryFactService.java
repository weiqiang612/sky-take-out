package com.weiqiang.skyai.memory.service;

import com.weiqiang.skyai.memory.model.MemoryFactKey;
import com.weiqiang.skyai.memory.model.MemoryFactSourceType;
import com.weiqiang.skyai.memory.model.UserMemory;
import com.weiqiang.skyai.memory.model.UserMemoryFact;
import com.weiqiang.skyai.memory.repository.UserMemoryFactRepository;
import com.weiqiang.skyai.memory.repository.UserMemoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class UserMemoryFactService {

    private final UserMemoryFactRepository userMemoryFactRepository;
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

    public void upsertFact(String userId, MemoryFactKey factKey, String factValue, MemoryFactSourceType sourceType, Double confidence) {
        if (!StringUtils.hasText(userId) || factKey == null || !StringUtils.hasText(factValue)) {
            return;
        }
        UserMemoryFact fact = userMemoryFactRepository.findByUserIdAndFactKey(userId, factKey.value())
                .orElseGet(UserMemoryFact::new);
        fact.setUserId(userId);
        fact.setFactKey(factKey.value());
        fact.setFactValue(mergeValue(factKey, fact.getFactValue(), factValue));
        fact.setSourceType(sourceType == null ? MemoryFactSourceType.INFERRED : sourceType);
        fact.setConfidence(confidence);
        fact.setUpdatedAt(Instant.now());
        userMemoryFactRepository.save(fact);
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
