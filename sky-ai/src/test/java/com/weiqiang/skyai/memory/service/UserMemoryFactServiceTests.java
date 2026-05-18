package com.weiqiang.skyai.memory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiqiang.skyai.memory.model.MemoryFactKey;
import com.weiqiang.skyai.memory.model.MemoryFactSourceType;
import com.weiqiang.skyai.memory.model.UserMemory;
import com.weiqiang.skyai.memory.model.UserMemoryFact;
import com.weiqiang.skyai.memory.model.UserMemoryFactHistory;
import com.weiqiang.skyai.memory.repository.UserMemoryFactRepository;
import com.weiqiang.skyai.memory.repository.UserMemoryFactHistoryRepository;
import com.weiqiang.skyai.memory.repository.UserMemoryRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
    void userProfileNotesSummaryUsesFirstSentenceAndNormalizesWhitespace() {
        UserMemoryFactRepository factRepository = mock(UserMemoryFactRepository.class);
        UserMemoryFactHistoryRepository historyRepository = mock(UserMemoryFactHistoryRepository.class);
        UserMemoryRepository userMemoryRepository = mock(UserMemoryRepository.class);
        UserMemoryFactService service = new UserMemoryFactService(factRepository, historyRepository, userMemoryRepository);

        UserMemoryFact profile = new UserMemoryFact();
        profile.setUserId("u1");
        profile.setFactKey(MemoryFactKey.USER_PROFILE_NOTES.value());
        profile.setFactValue("我在减脂。\n第二句应该被截断。");
        profile.setUpdatedAt(Instant.parse("2026-05-10T00:00:00Z"));

        when(factRepository.findAllByUserIdOrderByUpdatedAtDesc("u1")).thenReturn(List.of(profile));

        String summary = service.userProfileNotesSummary("u1");

        assertEquals("我在减脂。", summary);
    }

    @Test
    void userProfileNotesDetailedTruncatesToFiveHundredChars() {
        UserMemoryFactRepository factRepository = mock(UserMemoryFactRepository.class);
        UserMemoryFactHistoryRepository historyRepository = mock(UserMemoryFactHistoryRepository.class);
        UserMemoryRepository userMemoryRepository = mock(UserMemoryRepository.class);
        UserMemoryFactService service = new UserMemoryFactService(factRepository, historyRepository, userMemoryRepository);

        String longProfile = "a".repeat(520);
        UserMemoryFact profile = new UserMemoryFact();
        profile.setUserId("u1");
        profile.setFactKey(MemoryFactKey.USER_PROFILE_NOTES.value());
        profile.setFactValue(longProfile);
        profile.setUpdatedAt(Instant.parse("2026-05-10T00:00:00Z"));

        when(factRepository.findAllByUserIdOrderByUpdatedAtDesc("u1")).thenReturn(List.of(profile));

        String detailed = service.userProfileNotesDetailed("u1");

        assertEquals(500, detailed.length());
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

    @Test
    void upsertFactFromJsonWritesHistoryAndRefreshesSummary() throws Exception {
        UserMemoryFactRepository factRepository = mock(UserMemoryFactRepository.class);
        UserMemoryFactHistoryRepository historyRepository = mock(UserMemoryFactHistoryRepository.class);
        UserMemoryRepository userMemoryRepository = mock(UserMemoryRepository.class);
        UserMemoryFactService service = new UserMemoryFactService(factRepository, historyRepository, userMemoryRepository);

        UserMemoryFact existing = new UserMemoryFact();
        existing.setUserId("u1");
        existing.setFactKey(MemoryFactKey.DEFAULT_ADDRESS.value());
        existing.setFactValue("{\"raw\":\"Old address\"}");
        existing.setSourceType(MemoryFactSourceType.INFERRED);
        existing.setUpdatedAt(Instant.parse("2026-05-10T00:00:00Z"));

        when(factRepository.findByUserIdAndFactKey("u1", MemoryFactKey.DEFAULT_ADDRESS.value())).thenReturn(Optional.of(existing));
        when(factRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(userMemoryRepository.findById("u1")).thenReturn(Optional.empty());
        when(userMemoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.upsertFact("u1", MemoryFactKey.DEFAULT_ADDRESS, new ObjectMapper().readTree("{\"raw\":\"New address\"}"), MemoryFactSourceType.USER_MANUAL, 1.0, true);

        ArgumentCaptor<UserMemoryFactHistory> historyCaptor = ArgumentCaptor.forClass(UserMemoryFactHistory.class);
        verify(historyRepository).save(historyCaptor.capture());
        assertEquals("{\"raw\":\"Old address\"}", historyCaptor.getValue().getOldValue());
        assertEquals("{\"raw\":\"New address\"}", historyCaptor.getValue().getNewValue());
        assertEquals(MemoryFactSourceType.USER_MANUAL, historyCaptor.getValue().getSourceType());
        assertEquals("{\"raw\":\"New address\"}", existing.getFactValue());
        assertEquals(MemoryFactSourceType.USER_MANUAL, existing.getSourceType());
        verify(userMemoryRepository).save(any(UserMemory.class));
    }

    @Test
    void deleteFactWritesHistoryAndRefreshesSummary() {
        UserMemoryFactRepository factRepository = mock(UserMemoryFactRepository.class);
        UserMemoryFactHistoryRepository historyRepository = mock(UserMemoryFactHistoryRepository.class);
        UserMemoryRepository userMemoryRepository = mock(UserMemoryRepository.class);
        UserMemoryFactService service = new UserMemoryFactService(factRepository, historyRepository, userMemoryRepository);

        UserMemoryFact existing = new UserMemoryFact();
        existing.setUserId("u1");
        existing.setFactKey(MemoryFactKey.OPERATIONAL_NOTES.value());
        existing.setFactValue("Need no onion");
        existing.setSourceType(MemoryFactSourceType.USER_MANUAL);
        existing.setUpdatedAt(Instant.parse("2026-05-10T00:00:00Z"));

        when(factRepository.findByUserIdAndFactKey("u1", MemoryFactKey.OPERATIONAL_NOTES.value())).thenReturn(Optional.of(existing));
        when(factRepository.findAllByUserIdOrderByUpdatedAtDesc("u1")).thenReturn(List.of());
        when(userMemoryRepository.findById("u1")).thenReturn(Optional.empty());
        when(userMemoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.deleteFact("u1", MemoryFactKey.OPERATIONAL_NOTES);

        ArgumentCaptor<UserMemoryFactHistory> historyCaptor = ArgumentCaptor.forClass(UserMemoryFactHistory.class);
        verify(historyRepository).save(historyCaptor.capture());
        assertEquals("Need no onion", historyCaptor.getValue().getOldValue());
        assertEquals(MemoryFactSourceType.USER_MANUAL, historyCaptor.getValue().getSourceType());
        verify(factRepository).delete(existing);
        verify(userMemoryRepository).save(any(UserMemory.class));
    }

    @Test
    void findFactsSortedPrioritizesManualFacts() {
        UserMemoryFactRepository factRepository = mock(UserMemoryFactRepository.class);
        UserMemoryFactHistoryRepository historyRepository = mock(UserMemoryFactHistoryRepository.class);
        UserMemoryRepository userMemoryRepository = mock(UserMemoryRepository.class);
        UserMemoryFactService service = new UserMemoryFactService(factRepository, historyRepository, userMemoryRepository);

        UserMemoryFact inferred = new UserMemoryFact();
        inferred.setFactKey(MemoryFactKey.FAVORITE_DISHES.value());
        inferred.setFactValue("Mapo tofu");
        inferred.setSourceType(MemoryFactSourceType.INFERRED);
        inferred.setConfidence(0.9);
        inferred.setUpdatedAt(Instant.parse("2026-05-10T00:00:01Z"));

        UserMemoryFact manual = new UserMemoryFact();
        manual.setFactKey(MemoryFactKey.DEFAULT_ADDRESS.value());
        manual.setFactValue("{\"raw\":\"Manual address\"}");
        manual.setSourceType(MemoryFactSourceType.USER_MANUAL);
        manual.setConfidence(0.7);
        manual.setUpdatedAt(Instant.parse("2026-05-10T00:00:00Z"));

        UserMemoryFact user = new UserMemoryFact();
        user.setFactKey(MemoryFactKey.FAVORITE_FLAVORS.value());
        user.setFactValue("清淡");
        user.setSourceType(MemoryFactSourceType.USER);
        user.setConfidence(0.95);
        user.setUpdatedAt(Instant.parse("2026-05-10T00:00:02Z"));

        when(factRepository.findAllByUserIdOrderByUpdatedAtDesc("u1")).thenReturn(List.of(user, inferred, manual));

        List<UserMemoryFact> sorted = service.findFactsSorted("u1");

        assertEquals(MemoryFactSourceType.USER_MANUAL, sorted.get(0).getSourceType());
        assertEquals(MemoryFactSourceType.USER, sorted.get(1).getSourceType());
        assertEquals(MemoryFactSourceType.INFERRED, sorted.get(2).getSourceType());
    }

    @Test
    void findHistoryReturnsNewestFirst() {
        UserMemoryFactRepository factRepository = mock(UserMemoryFactRepository.class);
        UserMemoryFactHistoryRepository historyRepository = mock(UserMemoryFactHistoryRepository.class);
        UserMemoryRepository userMemoryRepository = mock(UserMemoryRepository.class);
        UserMemoryFactService service = new UserMemoryFactService(factRepository, historyRepository, userMemoryRepository);

        UserMemoryFactHistory older = new UserMemoryFactHistory();
        older.setChangedAt(Instant.parse("2026-05-10T00:00:00Z"));
        UserMemoryFactHistory newer = new UserMemoryFactHistory();
        newer.setChangedAt(Instant.parse("2026-05-11T00:00:00Z"));

        when(historyRepository.findAllByUserIdAndFactKeyOrderByChangedAtDesc("u1", MemoryFactKey.DEFAULT_ADDRESS.value()))
                .thenReturn(List.of(newer, older));

        List<UserMemoryFactHistory> history = service.findHistory("u1", MemoryFactKey.DEFAULT_ADDRESS);

        assertEquals(newer, history.get(0));
        assertEquals(older, history.get(1));
    }
}
