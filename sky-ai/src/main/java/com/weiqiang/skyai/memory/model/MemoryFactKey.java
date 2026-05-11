package com.weiqiang.skyai.memory.model;

public enum MemoryFactKey {

    FAVORITE_DISHES("favorite_dishes"),
    FAVORITE_FLAVORS("favorite_flavors"),
    DIETARY_RESTRICTIONS("dietary_restrictions"),
    DEFAULT_ADDRESS("default_address"),
    OPERATIONAL_NOTES("operational_notes");

    private final String value;

    MemoryFactKey(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
