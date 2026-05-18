package com.weiqiang.skyai.advisor;

import com.weiqiang.skyai.intent_recognition.model.ConfidenceLevel;
import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionResult;
import com.weiqiang.skyai.intent_recognition.model.IntentType;
import com.weiqiang.skyai.memory.config.UserProfileMemoryProperties;
import com.weiqiang.skyai.memory.model.MemoryFactKey;
import com.weiqiang.skyai.memory.model.MemoryFactSourceType;
import com.weiqiang.skyai.memory.model.UserMemoryFact;
import com.weiqiang.skyai.memory.service.UserMemoryFactService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserContextAdvisorTests {

    @Test
    void buildContextBlockInjectsSummaryForOrderStatus() {
        UserMemoryFactService userMemoryFactService = mock(UserMemoryFactService.class);
        UserProfileMemoryProperties properties = new UserProfileMemoryProperties();
        UserProfileInjectionMetrics metrics = mock(UserProfileInjectionMetrics.class);
        UserContextAdvisor advisor = new UserContextAdvisor(userMemoryFactService, properties, metrics);

        when(userMemoryFactService.userProfileNotesSummary("u1")).thenReturn("我在减脂");

        IntentRecognitionResult intentResult = new IntentRecognitionResult(
                IntentType.ORDER_STATUS,
                ConfidenceLevel.HIGH,
                Map.of("order_id", "A123"),
                List.of(IntentType.ORDER_STATUS),
                null,
                false,
                null
        );

        String block = ReflectionTestUtils.invokeMethod(advisor, "buildContextBlock", intentResult, "u1");

        assertTrue(block.contains("Relevant memory"));
        assertTrue(block.contains("User profile notes"));
        assertTrue(block.contains("我在减脂"));
        assertTrue(block.contains("Order id: A123"));
    }

    @Test
    void buildContextBlockSkipsProfileForShopStatus() {
        UserMemoryFactService userMemoryFactService = mock(UserMemoryFactService.class);
        UserProfileMemoryProperties properties = new UserProfileMemoryProperties();
        UserProfileInjectionMetrics metrics = mock(UserProfileInjectionMetrics.class);
        UserContextAdvisor advisor = new UserContextAdvisor(userMemoryFactService, properties, metrics);

        when(userMemoryFactService.userProfileNotesSummary("u1")).thenReturn("我在减脂");

        IntentRecognitionResult intentResult = new IntentRecognitionResult(
                IntentType.SHOP_STATUS,
                ConfidenceLevel.HIGH,
                Map.of(),
                List.of(IntentType.SHOP_STATUS),
                null,
                false,
                null
        );

        String block = ReflectionTestUtils.invokeMethod(advisor, "buildContextBlock", intentResult, "u1");

        assertTrue(block == null || block.isEmpty());
    }

    @Test
    void buildContextBlockUsesDetailedProfileForMenuQueryBeforeOtherMemory() {
        UserMemoryFactService userMemoryFactService = mock(UserMemoryFactService.class);
        UserProfileMemoryProperties properties = new UserProfileMemoryProperties();
        UserProfileInjectionMetrics metrics = mock(UserProfileInjectionMetrics.class);
        UserContextAdvisor advisor = new UserContextAdvisor(userMemoryFactService, properties, metrics);

        UserMemoryFact profile = new UserMemoryFact();
        profile.setFactKey(MemoryFactKey.USER_PROFILE_NOTES.value());
        profile.setFactValue("我在减脂。第二句应该被保留。");
        profile.setSourceType(MemoryFactSourceType.USER_MANUAL);
        profile.setConfidence(1.0);
        profile.setUpdatedAt(Instant.parse("2026-05-11T00:00:00Z"));

        UserMemoryFact favoriteDish = new UserMemoryFact();
        favoriteDish.setFactKey(MemoryFactKey.FAVORITE_DISHES.value());
        favoriteDish.setFactValue("沙拉");
        favoriteDish.setSourceType(MemoryFactSourceType.USER);
        favoriteDish.setConfidence(0.9);
        favoriteDish.setUpdatedAt(Instant.parse("2026-05-10T00:00:00Z"));

        when(userMemoryFactService.userProfileNotesDetailed("u1")).thenReturn("我在减脂。第二句应该被保留。");
        when(userMemoryFactService.findFactsSorted("u1")).thenReturn(List.of(profile, favoriteDish));

        IntentRecognitionResult intentResult = new IntentRecognitionResult(
                IntentType.MENU_QUERY,
                ConfidenceLevel.HIGH,
                Map.of(),
                List.of(IntentType.MENU_QUERY),
                null,
                false,
                null
        );

        String block = ReflectionTestUtils.invokeMethod(advisor, "buildContextBlock", intentResult, "u1");

        assertTrue(block.contains("我在减脂。第二句应该被保留。"));
        assertTrue(block.contains("Favorite dishes"));
        assertTrue(block.indexOf("User profile notes") < block.indexOf("Favorite dishes"));
        assertTrue(block.contains("If the user names a dish or setmeal"));
    }

    @Test
    void buildContextBlockRespectsDisabledProfileSwitch() {
        UserMemoryFactService userMemoryFactService = mock(UserMemoryFactService.class);
        UserProfileMemoryProperties properties = new UserProfileMemoryProperties();
        properties.setEnabled(false);
        UserProfileInjectionMetrics metrics = mock(UserProfileInjectionMetrics.class);
        UserContextAdvisor advisor = new UserContextAdvisor(userMemoryFactService, properties, metrics);

        UserMemoryFact favoriteDish = new UserMemoryFact();
        favoriteDish.setFactKey(MemoryFactKey.FAVORITE_DISHES.value());
        favoriteDish.setFactValue("沙拉");
        favoriteDish.setSourceType(MemoryFactSourceType.USER);
        favoriteDish.setConfidence(0.9);
        favoriteDish.setUpdatedAt(Instant.parse("2026-05-10T00:00:00Z"));

        when(userMemoryFactService.findFactsSorted("u1")).thenReturn(List.of(favoriteDish));

        IntentRecognitionResult intentResult = new IntentRecognitionResult(
                IntentType.OTHER,
                ConfidenceLevel.LOW,
                Map.of(),
                List.of(IntentType.OTHER),
                null,
                false,
                null
        );

        String block = ReflectionTestUtils.invokeMethod(advisor, "buildContextBlock", intentResult, "u1");

        assertTrue(block == null || block.isEmpty());
    }
}
