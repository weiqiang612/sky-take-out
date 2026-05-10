package com.weiqiang.skyai.memory.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "user_memory")
public class UserMemory {

    @Id
    @Column(nullable = false, unique = true)
    private String userId;

    @Column(nullable = true)
    private String dietaryPrefs;

    @Column(nullable = true)
    private String defaultAddress;

    @Column(length = 500, nullable = true)
    private String knownIssues;

    @Column(nullable = false)
    private Instant updatedAt;
}
