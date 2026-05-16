package com.weiqiang.skyai.memory.repository;

import com.weiqiang.skyai.memory.model.UserMemoryFactHistory;
import com.weiqiang.skyai.memory.model.UserMemoryFactHistoryId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserMemoryFactHistoryRepository extends JpaRepository<UserMemoryFactHistory, UserMemoryFactHistoryId> {
}
