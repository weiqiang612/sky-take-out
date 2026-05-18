package com.weiqiang.skyai.memory.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.weiqiang.skyai.memory.model.MemoryFactSourceType;

import java.time.Instant;

/**
 * Response payload for a memory fact change entry.
 *
 * @param oldValue the previous value
 * @param newValue the new value
 * @param sourceType the source type associated with the change
 * @param changedAt change timestamp
 */
public record MemoryFactHistoryResponse(
        JsonNode oldValue,
        JsonNode newValue,
        MemoryFactSourceType sourceType,
        Instant changedAt) {
}
