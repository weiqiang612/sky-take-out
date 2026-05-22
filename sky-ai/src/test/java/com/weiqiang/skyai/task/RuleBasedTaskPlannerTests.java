package com.weiqiang.skyai.task;

import com.weiqiang.skyai.intent_recognition.model.ConfidenceLevel;
import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionResult;
import com.weiqiang.skyai.intent_recognition.model.IntentType;
import com.weiqiang.skyai.task.model.TaskPlanningResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleBasedTaskPlannerTests {

    @Test
    void planShouldSplitBatchCancelIntoSingleOrderSteps() {
        RuleBasedTaskPlanner planner = new RuleBasedTaskPlanner();
        IntentRecognitionResult intent = new IntentRecognitionResult(
                IntentType.CANCEL_ORDER,
                ConfidenceLevel.HIGH,
                Map.of("order_ids", "1779351452612,1779341664613"),
                List.of(IntentType.CANCEL_ORDER),
                null,
                false,
                null
        );

        TaskPlanningResult result = planner.plan("取消这两个订单吧", intent, List.of());

        assertTrue(result.decomposed());
        assertEquals(2, result.plan().steps().size());
        assertEquals(Map.of("order_id", "1779351452612"), result.plan().steps().get(0).entities());
        assertEquals(Map.of("order_id", "1779341664613"), result.plan().steps().get(1).entities());
        assertEquals("请取消目标订单。 订单号：1779351452612。", result.plan().steps().get(0).instruction());
        assertEquals("请取消目标订单。 订单号：1779341664613。", result.plan().steps().get(1).instruction());
        assertFalse(result.plan().steps().get(0).requiresConfirmation());
        assertFalse(result.plan().steps().get(1).requiresConfirmation());
    }

    @Test
    void planShouldKeepSingleOrderCancelUnsplit() {
        RuleBasedTaskPlanner planner = new RuleBasedTaskPlanner();
        IntentRecognitionResult intent = new IntentRecognitionResult(
                IntentType.CANCEL_ORDER,
                ConfidenceLevel.HIGH,
                Map.of("order_id", "1779351452612"),
                List.of(IntentType.CANCEL_ORDER),
                null,
                false,
                null
        );

        TaskPlanningResult result = planner.plan("取消这个订单吧", intent, List.of());

        assertFalse(result.decomposed());
        assertNull(result.plan());
    }

    @Test
    void planShouldBuildMultiIntentPlanWithoutConnectorWords() {
        RuleBasedTaskPlanner planner = new RuleBasedTaskPlanner();
        IntentRecognitionResult intent = new IntentRecognitionResult(
                IntentType.ORDER_STATUS,
                ConfidenceLevel.HIGH,
                Map.of(),
                List.of(IntentType.ORDER_STATUS, IntentType.CANCEL_ORDER),
                null,
                false,
                null
        );

        TaskPlanningResult result = planner.plan("查一下再取消", intent, List.of());

        assertTrue(result.decomposed());
        assertEquals(2, result.plan().steps().size());
        assertEquals(IntentType.ORDER_STATUS, result.plan().steps().get(0).intent());
        assertEquals(IntentType.CANCEL_ORDER, result.plan().steps().get(1).intent());
    }

    @Test
    void planShouldBuildLookupDrivenCancelPlanForRecentUndeliveredOrders() {
        RuleBasedTaskPlanner planner = new RuleBasedTaskPlanner();
        IntentRecognitionResult intent = new IntentRecognitionResult(
                IntentType.CANCEL_ORDER,
                ConfidenceLevel.HIGH,
                Map.of("order_count", "2", "order_status", "not_delivered"),
                List.of(IntentType.ORDER_STATUS, IntentType.CANCEL_ORDER),
                null,
                false,
                null
        );

        TaskPlanningResult result = planner.plan("帮我看看最近的两个没有送到的订单，给我退掉", intent, List.of());

        assertTrue(result.decomposed());
        assertEquals(3, result.plan().steps().size());
        assertEquals(IntentType.ORDER_STATUS, result.plan().steps().get(0).intent());
        assertEquals(IntentType.CANCEL_ORDER, result.plan().steps().get(1).intent());
        assertEquals(IntentType.CANCEL_ORDER, result.plan().steps().get(2).intent());
        assertFalse(result.plan().steps().get(0).requiresConfirmation());
        assertTrue(result.plan().steps().get(1).requiresConfirmation());
        assertFalse(result.plan().steps().get(2).requiresConfirmation());
    }
}
