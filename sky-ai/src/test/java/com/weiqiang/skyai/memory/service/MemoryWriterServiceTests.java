package com.weiqiang.skyai.memory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiqiang.skyai.intent_recognition.model.IntentType;
import com.weiqiang.skyai.memory.model.MemoryFactKey;
import com.weiqiang.skyai.memory.model.MemoryFactSourceType;
import com.weiqiang.skyai.memory.repository.RedisChatMemoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class MemoryWriterServiceTests {

    @Test
    void persistToolOutcomeWritesRefundAndAddressFacts() {
        ChatClient.Builder chatClientBuilder = mock(ChatClient.Builder.class);
        RedisChatMemoryRepository redisChatMemoryRepository = mock(RedisChatMemoryRepository.class);
        UserMemoryFactService userMemoryFactService = mock(UserMemoryFactService.class);
        MemoryWriterService service = new MemoryWriterService(chatClientBuilder, redisChatMemoryRepository, userMemoryFactService, new ObjectMapper());

        ReflectionTestUtils.invokeMethod(service, "persistToolOutcome", "u1", IntentType.REQUEST_REFUND, "Refund requested for order 99: late delivery");
        ReflectionTestUtils.invokeMethod(service, "persistToolOutcome", "u1", IntentType.CHANGE_ADDRESS, "Updated delivery address for order 99: No. 1 Road");

        verify(userMemoryFactService).upsertFact(eq("u1"), eq(MemoryFactKey.OPERATIONAL_NOTES), eq("已为订单 99 退款：late delivery"), eq(MemoryFactSourceType.TOOL), isNull());
        verify(userMemoryFactService).upsertFact(eq("u1"), eq(MemoryFactKey.DEFAULT_ADDRESS), eq("No. 1 Road"), eq(MemoryFactSourceType.TOOL), isNull());
    }

    @Test
    void persistToolOutcomeWritesDefaultAddressSnapshotForQuery() {
        ChatClient.Builder chatClientBuilder = mock(ChatClient.Builder.class);
        RedisChatMemoryRepository redisChatMemoryRepository = mock(RedisChatMemoryRepository.class);
        UserMemoryFactService userMemoryFactService = mock(UserMemoryFactService.class);
        MemoryWriterService service = new MemoryWriterService(chatClientBuilder, redisChatMemoryRepository, userMemoryFactService, new ObjectMapper());

        String responseData = """
                {"id":1,"userId":7,"consignee":"张三","phone":"13800000000","detail":"朝阳区建国路1号","label":"家","isDefault":1}
                """;
        ReflectionTestUtils.invokeMethod(service, "persistToolOutcome", "u1", IntentType.ADDRESS_MANAGEMENT, responseData);

        verify(userMemoryFactService).upsertFact(eq("u1"), eq(MemoryFactKey.DEFAULT_ADDRESS), eq("朝阳区建国路1号"), eq(MemoryFactSourceType.TOOL), isNull());
    }

    @Test
    void persistToolOutcomeSkipsFailures() {
        ChatClient.Builder chatClientBuilder = mock(ChatClient.Builder.class);
        RedisChatMemoryRepository redisChatMemoryRepository = mock(RedisChatMemoryRepository.class);
        UserMemoryFactService userMemoryFactService = mock(UserMemoryFactService.class);
        MemoryWriterService service = new MemoryWriterService(chatClientBuilder, redisChatMemoryRepository, userMemoryFactService, new ObjectMapper());

        ReflectionTestUtils.invokeMethod(service, "persistToolOutcome", "u1", IntentType.CANCEL_ORDER, "FAIL: server rejected");

        verifyNoInteractions(userMemoryFactService);
    }

    @Test
    void extractionPromptMentionsUserManagedFactsAndExplicitStatements() throws Exception {
        Field field = MemoryWriterService.class.getDeclaredField("EXTRACTION_SYSTEM_PROMPT");
        field.setAccessible(true);
        String prompt = (String) field.get(null);

        org.junit.jupiter.api.Assertions.assertTrue(prompt.contains("explicit statements only"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.contains("User-managed facts may also be edited or deleted later from the UI"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.contains("historical facts"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.contains("不要写成请求句") || prompt.contains("request style"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.contains("曾经"));
    }

    @Test
    void persistToolOutcomeWritesHistoricalStyleCancelNote() {
        ChatClient.Builder chatClientBuilder = mock(ChatClient.Builder.class);
        RedisChatMemoryRepository redisChatMemoryRepository = mock(RedisChatMemoryRepository.class);
        UserMemoryFactService userMemoryFactService = mock(UserMemoryFactService.class);
        MemoryWriterService service = new MemoryWriterService(chatClientBuilder, redisChatMemoryRepository, userMemoryFactService, new ObjectMapper());

        String today = (String) ReflectionTestUtils.invokeMethod(service, "today");
        ReflectionTestUtils.invokeMethod(service, "persistToolOutcome", "u1", IntentType.CANCEL_ORDER, "Order cancelled for order 88");

        verify(userMemoryFactService).upsertFact(eq("u1"), eq(MemoryFactKey.OPERATIONAL_NOTES), eq("已取消订单 88（" + today + "）"), eq(MemoryFactSourceType.TOOL), isNull());
    }
}
