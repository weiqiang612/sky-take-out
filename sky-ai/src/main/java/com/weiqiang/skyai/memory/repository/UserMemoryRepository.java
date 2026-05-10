package com.weiqiang.skyai.memory.repository;

import com.weiqiang.skyai.memory.model.UserMemory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserMemoryRepository extends JpaRepository<UserMemory, String> {
}
