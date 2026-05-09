package com.weiqiang.skyai.intent_recognition.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ConfidenceLevel {
    HIGH("high"),
    MEDIUM("medium"),
    LOW("low");

    private final String value;

    ConfidenceLevel(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static ConfidenceLevel fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (ConfidenceLevel level : values()) {
            if (level.value.equalsIgnoreCase(value.trim())) {
                return level;
            }
        }
        throw new IllegalArgumentException("Unknown confidence level: " + value);
    }
}
