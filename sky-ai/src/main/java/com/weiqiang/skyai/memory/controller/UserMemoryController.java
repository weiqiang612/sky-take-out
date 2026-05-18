package com.weiqiang.skyai.memory.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiqiang.skyai.memory.dto.MemoryFactHistoryResponse;
import com.weiqiang.skyai.memory.dto.MemoryFactResponse;
import com.weiqiang.skyai.memory.dto.MemoryFactUpdateRequest;
import com.weiqiang.skyai.memory.model.MemoryFactKey;
import com.weiqiang.skyai.memory.model.MemoryFactSourceType;
import com.weiqiang.skyai.memory.model.UserMemoryFact;
import com.weiqiang.skyai.memory.model.UserMemoryFactHistory;
import com.weiqiang.skyai.memory.service.UserMemoryFactService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * REST API for managing a user's persistent memory facts.
 */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/{userId}/memory")
public class UserMemoryController {

    private final UserMemoryFactService userMemoryFactService;
    private final ObjectMapper objectMapper;

    /**
     * Lists all memory facts for a user in display order.
     *
     * @param userId the user identifier
     * @return the user's memory facts
     */
    @GetMapping
    public List<MemoryFactResponse> listFacts(@PathVariable String userId) {
        return userMemoryFactService.findFactsSorted(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Creates or updates a user-managed fact.
     *
     * @param userId the user identifier
     * @param factKey the fact key in lower snake case
     * @param request the JSON payload
     * @return the updated memory fact
     */
    @PutMapping("/{factKey}")
    public MemoryFactResponse upsertFact(@PathVariable String userId,
                                         @PathVariable String factKey,
                                         @Valid @RequestBody MemoryFactUpdateRequest request) {
        MemoryFactKey resolvedFactKey = resolveFactKey(factKey);
        validateFactValue(resolvedFactKey, request.factValue());
        userMemoryFactService.upsertFact(userId, resolvedFactKey, request.factValue(), MemoryFactSourceType.USER_MANUAL, 1.0, true);
        return userMemoryFactService.findFact(userId, resolvedFactKey)
                .map(this::toResponse)
                .orElseThrow(() -> new IllegalStateException("memory fact was not saved"));
    }

    /**
     * Deletes a user-managed fact.
     *
     * @param userId the user identifier
     * @param factKey the fact key in lower snake case
     * @return an empty response
     */
    @DeleteMapping("/{factKey}")
    public ResponseEntity<Void> deleteFact(@PathVariable String userId, @PathVariable String factKey) {
        userMemoryFactService.deleteFact(userId, resolveFactKey(factKey));
        return ResponseEntity.noContent().build();
    }

    /**
     * Returns the audit trail for a fact.
     *
     * @param userId the user identifier
     * @param factKey the fact key in lower snake case
     * @return the history rows ordered by newest first
     */
    @GetMapping("/{factKey}/history")
    public List<MemoryFactHistoryResponse> history(@PathVariable String userId, @PathVariable String factKey) {
        MemoryFactKey resolvedFactKey = resolveFactKey(factKey);
        return userMemoryFactService.findHistory(userId, resolvedFactKey).stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    private MemoryFactKey resolveFactKey(String factKey) {
        if (!StringUtils.hasText(factKey)) {
            throw new IllegalArgumentException("factKey must be one of " + supportedFactKeys());
        }
        return MemoryFactKey.fromValue(factKey);
    }

    private String supportedFactKeys() {
        return Arrays.stream(MemoryFactKey.values())
                .map(MemoryFactKey::value)
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    private void validateFactValue(MemoryFactKey factKey, JsonNode factValue) {
        if (factValue == null || factValue.isNull()) {
            throw new IllegalArgumentException("factValue is required");
        }
        Set<MemoryFactKey> arrayKeys = Set.of(MemoryFactKey.FAVORITE_DISHES, MemoryFactKey.DIETARY_RESTRICTIONS);
        if (arrayKeys.contains(factKey)) {
            if (!factValue.isArray()) {
                throw new IllegalArgumentException(factKey.value() + " must be a JSON array");
            }
            return;
        }
        if (factKey == MemoryFactKey.DEFAULT_ADDRESS) {
            if (!factValue.isObject() || !factValue.hasNonNull("raw") || !factValue.get("raw").isTextual()) {
                throw new IllegalArgumentException("default_address must be a JSON object with raw");
            }
            return;
        }
        if (factKey == MemoryFactKey.FAVORITE_FLAVORS || factKey == MemoryFactKey.OPERATIONAL_NOTES) {
            if (!factValue.isTextual()) {
                throw new IllegalArgumentException(factKey.value() + " must be a JSON string");
            }
            return;
        }
        if (factKey == MemoryFactKey.USER_PROFILE_NOTES) {
            if (!factValue.isTextual()) {
                throw new IllegalArgumentException(factKey.value() + " must be a JSON string");
            }
        }
    }

    private MemoryFactResponse toResponse(UserMemoryFact fact) {
        return new MemoryFactResponse(
                fact.getFactKey(),
                toJsonNode(fact.getFactValue()),
                fact.getConfidence(),
                fact.getSourceType(),
                fact.getCreatedAt(),
                fact.getUpdatedAt());
    }

    private MemoryFactHistoryResponse toHistoryResponse(UserMemoryFactHistory history) {
        return new MemoryFactHistoryResponse(
                toJsonNode(history.getOldValue()),
                toJsonNode(history.getNewValue()),
                history.getSourceType(),
                history.getChangedAt());
    }

    private JsonNode toJsonNode(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception ex) {
            return objectMapper.getNodeFactory().textNode(value);
        }
    }
}
