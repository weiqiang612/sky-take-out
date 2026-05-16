package com.weiqiang.skyai.memory.service;

import com.weiqiang.skyai.memory.model.MemoryFactKey;
import com.weiqiang.skyai.memory.model.MemoryFactSourceType;
import com.weiqiang.skyai.memory.model.UserMemory;
import com.weiqiang.skyai.memory.model.UserMemoryFact;
import com.weiqiang.skyai.memory.model.UserMemoryFactHistory;
import com.weiqiang.skyai.memory.repository.UserMemoryFactRepository;
import com.weiqiang.skyai.memory.repository.UserMemoryFactHistoryRepository;
import com.weiqiang.skyai.memory.repository.UserMemoryRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserMemoryFactServiceTests {

    @Test
    void upsertFactAppendsOperationalNotesAndRefreshesSummary() {
        UserMemoryFactRepository factRepository = mock(UserMemoryFactRepository.class);
        UserMemoryFactHistoryRepository historyRepository = mock(UserMemoryFactHistoryRepository.class);
        UserMemoryRepository userMemoryRepository = mock(UserMemoryRepository.class);
        UserMemoryFactService service = new UserMemoryFactService(factRepository, historyRepository, userMemoryRepository);

        UserMemoryFact existing = new UserMemoryFact();
        existing.setUserId("u1");
        existing.setFactKey(MemoryFactKey.OPERATIONAL_NOTES.value());
        existing.setFactValue("Cancelled order 11 on 2026-05-10");
        existing.setUpdatedAt(Instant.parse("2026-05-10T00:00:00Z"));

        UserMemory userMemory = new UserMemory();
        when(factRepository.findByUserIdAndFactKey("u1", MemoryFactKey.OPERATIONAL_NOTES.value())).thenReturn(Optional.of(existing));
        when(factRepository.findAllByUserIdOrderByUpdatedAtDesc("u1")).thenAnswer(invocation -> List.of(existing));
        when(factRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(userMemoryRepository.findById("u1")).thenReturn(Optional.of(userMemory));
        when(userMemoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.upsertFact("u1", MemoryFactKey.OPERATIONAL_NOTES, "Refund issued for order 12: late delivery", MemoryFactSourceType.TOOL, null);

        assertTrue(existing.getFactValue().contains("Cancelled order 11 on 2026-05-10"));
        assertTrue(existing.getFactValue().contains("Refund issued for order 12: late delivery"));
        assertTrue(userMemory.getKnownIssues().contains("Refund issued for order 12: late delivery"));
        verify(userMemoryRepository).save(userMemory);
    }

    @Test
    void dietaryPreferencesSummaryCombinesStructuredFacts() {
        UserMemoryFactRepository factRepository = mock(UserMemoryFactRepository.class);
        UserMemoryFactHistoryRepository historyRepository = mock(UserMemoryFactHistoryRepository.class);
        UserMemoryRepository userMemoryRepository = mock(UserMemoryRepository.class);
        UserMemoryFactService service = new UserMemoryFactService(factRepository, historyRepository, userMemoryRepository);

        UserMemoryFact dish = new UserMemoryFact();
        dish.setUserId("u1");
        dish.setFactKey(MemoryFactKey.FAVORITE_DISHES.value());
        dish.setFactValue("平菇豆腐汤");
        dish.setUpdatedAt(Instant.parse("2026-05-10T00:00:00Z"));

        UserMemoryFact flavor = new UserMemoryFact();
        flavor.setUserId("u1");
        flavor.setFactKey(MemoryFactKey.FAVORITE_FLAVORS.value());
        flavor.setFactValue("清淡");
        flavor.setUpdatedAt(Instant.parse("2026-05-10T00:00:01Z"));

        when(factRepository.findAllByUserIdOrderByUpdatedAtDesc("u1")).thenReturn(List.of(flavor, dish));

        String summary = service.dietaryPreferencesSummary("u1");

        assertTrue(summary.contains("喜欢的菜：平菇豆腐汤"));
        assertTrue(summary.contains("口味偏好：清淡"));
    }

    @Test
    void upsertFactSkipsLowConfidence() {
        UserMemoryFactRepository factRepository = mock(UserMemoryFactRepository.class);
        UserMemoryFactHistoryRepository historyRepository = mock(UserMemoryFactHistoryRepository.class);
        UserMemoryRepository userMemoryRepository = mock(UserMemoryRepository.class);
        UserMemoryFactService service = new UserMemoryFactService(factRepository, historyRepository, userMemoryRepository);

        service.upsertFact("u1", MemoryFactKey.DEFAULT_ADDRESS, "No. 1 Road", MemoryFactSourceType.USER, 0.6);

        verifyNoInteractions(factRepository, historyRepository, userMemoryRepository);
    }

    @Test
    void upsertFactWritesHistoryForCorrections() {
        UserMemoryFactRepository factRepository = mock(UserMemoryFactRepository.class);
        UserMemoryFactHistoryRepository historyRepository = mock(UserMemoryFactHistoryRepository.class);
        UserMemoryRepository userMemoryRepository = mock(UserMemoryRepository.class);
        UserMemoryFactService service = new UserMemoryFactService(factRepository, historyRepository, userMemoryRepository);

        UserMemoryFact existing = new UserMemoryFact();
        existing.setUserId("u1");
        existing.setFactKey(MemoryFactKey.DEFAULT_ADDRESS.value());
        existing.setFactValue("Old address");
        existing.setUpdatedAt(Instant.parse("2026-05-10T00:00:00Z"));

        when(factRepository.findByUserIdAndFactKey("u1", MemoryFactKey.DEFAULT_ADDRESS.value())).thenReturn(Optional.of(existing));
        when(factRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(userMemoryRepository.findById("u1")).thenReturn(Optional.empty());
        when(userMemoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.upsertFact("u1", MemoryFactKey.DEFAULT_ADDRESS, "New address", MemoryFactSourceType.USER, 0.9, true);

        verify(historyRepository).save(any(UserMemoryFactHistory.class));
        assertEquals("New address", existing.getFactValue());
    }
}
