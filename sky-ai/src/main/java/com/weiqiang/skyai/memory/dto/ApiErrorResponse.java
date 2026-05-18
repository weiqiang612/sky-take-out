package com.weiqiang.skyai.memory.dto;

/**
 * Standard error payload for memory API validation failures.
 *
 * @param message the human-readable error message
 */
public record ApiErrorResponse(String message) {
}
