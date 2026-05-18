package com.weiqiang.skyai.memory.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload for user-managed fact updates.
 *
 * @param factValue the new JSON value
 */
public record MemoryFactUpdateRequest(
        @NotNull(message = "factValue is required")
        JsonNode factValue) {
}
