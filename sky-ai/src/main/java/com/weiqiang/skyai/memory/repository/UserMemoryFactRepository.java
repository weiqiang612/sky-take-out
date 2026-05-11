package com.weiqiang.skyai.memory.repository;

import com.weiqiang.skyai.memory.model.UserMemoryFact;
import com.weiqiang.skyai.memory.model.UserMemoryFactId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserMemoryFactRepository extends JpaRepository<UserMemoryFact, UserMemoryFactId> {

    List<UserMemoryFact> findAllByUserIdOrderByUpdatedAtDesc(String userId);

    Optional<UserMemoryFact> findByUserIdAndFactKey(String userId, String factKey);
}
