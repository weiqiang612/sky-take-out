package com.weiqiang.skyai.memory.model;

public enum MemoryFactSourceType {

    USER_MANUAL,
    USER,
    TOOL,
    INFERRED;

    /**
     * Returns the display order used by memory list and prompt injection sorting.
     *
     * @return lower values indicate higher priority
     */
    public int sortPriority() {
        return switch (this) {
            case USER_MANUAL -> 0;
            case USER -> 1;
            case TOOL -> 2;
            case INFERRED -> 3;
        };
    }
}
