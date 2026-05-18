package com.weiqiang.skyai.memory.repository;

import com.weiqiang.skyai.memory.model.UserMemoryFactHistory;
import com.weiqiang.skyai.memory.model.UserMemoryFactHistoryId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserMemoryFactHistoryRepository extends JpaRepository<UserMemoryFactHistory, UserMemoryFactHistoryId> {

    List<UserMemoryFactHistory> findAllByUserIdAndFactKeyOrderByChangedAtDesc(String userId, String factKey);
}
