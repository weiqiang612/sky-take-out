package com.weiqiang.skyai.memory.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

public class UserMemoryFactHistoryId implements Serializable {

    private String userId;
    private String factKey;
    private Instant changedAt;

    public UserMemoryFactHistoryId() {
    }

    public UserMemoryFactHistoryId(String userId, String factKey, Instant changedAt) {
        this.userId = userId;
        this.factKey = factKey;
        this.changedAt = changedAt;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getFactKey() {
        return factKey;
    }

    public void setFactKey(String factKey) {
        this.factKey = factKey;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(Instant changedAt) {
        this.changedAt = changedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        UserMemoryFactHistoryId that = (UserMemoryFactHistoryId) o;
        return Objects.equals(userId, that.userId) && Objects.equals(factKey, that.factKey) && Objects.equals(changedAt, that.changedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, factKey, changedAt);
    }
}
