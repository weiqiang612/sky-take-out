package com.weiqiang.skyai.memory.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.weiqiang.skyai.memory.model.MemoryFactSourceType;

import java.time.Instant;

/**
 * Response payload for a persisted memory fact.
 *
 * @param factKey the fact key
 * @param factValue the JSON value
 * @param confidence the confidence score
 * @param sourceType the fact source
 * @param createdAt creation time
 * @param updatedAt last update time
 */
public record MemoryFactResponse(
        String factKey,
        JsonNode factValue,
        Double confidence,
        MemoryFactSourceType sourceType,
        Instant createdAt,
        Instant updatedAt) {
}
