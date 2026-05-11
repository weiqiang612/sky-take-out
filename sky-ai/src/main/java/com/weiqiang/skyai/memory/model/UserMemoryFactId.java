package com.weiqiang.skyai.memory.model;

import java.io.Serializable;
import java.util.Objects;

public class UserMemoryFactId implements Serializable {

    private String userId;
    private String factKey;

    public UserMemoryFactId() {
    }

    public UserMemoryFactId(String userId, String factKey) {
        this.userId = userId;
        this.factKey = factKey;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        UserMemoryFactId that = (UserMemoryFactId) o;
        return Objects.equals(userId, that.userId) && Objects.equals(factKey, that.factKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, factKey);
    }
}
