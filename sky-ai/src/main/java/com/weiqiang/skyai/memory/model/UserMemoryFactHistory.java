package com.weiqiang.skyai.memory.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@IdClass(UserMemoryFactHistoryId.class)
@Table(name = "user_memory_fact_history")
public class UserMemoryFactHistory {

    @Id
    @Column(name = "user_id", nullable = false)
    private String userId;

    @Id
    @Column(name = "fact_key", nullable = false)
    private String factKey;

    @Id
    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(name = "old_value", columnDefinition = "text")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "text")
    private String newValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private MemoryFactSourceType sourceType;

    @Column(name = "confidence")
    private Double confidence;
}
