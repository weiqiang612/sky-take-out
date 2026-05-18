package com.weiqiang.skyai.memory.model;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

public enum MemoryFactKey {

    FAVORITE_DISHES("favorite_dishes"),
    FAVORITE_FLAVORS("favorite_flavors"),
    DIETARY_RESTRICTIONS("dietary_restrictions"),
    DEFAULT_ADDRESS("default_address"),
    OPERATIONAL_NOTES("operational_notes"),
    USER_PROFILE_NOTES("user_profile_notes");

    private final String value;

    MemoryFactKey(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /**
     * Resolves a lower-snake-case memory key into the matching enum value.
     *
     * @param value the external fact key value
     * @return the matching enum constant
     * @throws IllegalArgumentException when the key is not recognized
     */
    public static MemoryFactKey fromValue(String value) {
        return Arrays.stream(values())
                .filter(key -> Objects.equals(key.value, value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("factKey must be one of " + supportedValues()));
    }

    private static String supportedValues() {
        return Arrays.stream(values())
                .map(MemoryFactKey::value)
                .collect(Collectors.joining(", "));
    }
}
