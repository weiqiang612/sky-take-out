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
@IdClass(UserMemoryFactId.class)
@Table(name = "user_memory_fact")
public class UserMemoryFact {

    @Id
    @Column(name = "user_id", nullable = false)
    private String userId;

    @Id
    @Column(name = "fact_key", nullable = false)
    private String factKey;

    @Column(name = "fact_value", nullable = false, columnDefinition = "text")
    private String factValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private MemoryFactSourceType sourceType;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
