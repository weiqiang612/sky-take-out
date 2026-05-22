package com.weiqiang.skyai.task;

import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionResult;
import com.weiqiang.skyai.intent_recognition.model.IntentType;
import com.weiqiang.skyai.task.model.TaskExecutionOutcome;
import com.weiqiang.skyai.task.model.TaskExecutionState;
import com.weiqiang.skyai.task.model.TaskPlan;
import com.weiqiang.skyai.task.model.TaskStep;
import com.weiqiang.skyai.websocket.AgentChatService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskOrchestratorServiceTests {

    @Test
    void executePlanShouldUseStepInstructionAsPrompt() {
        TaskPlanner planner = mock(TaskPlanner.class);
        TaskExecutionStateRepository repository = mock(TaskExecutionStateRepository.class);
        AgentChatService agentChatService = mock(AgentChatService.class);
        TaskOrchestratorService service = new TaskOrchestratorService(planner, repository, agentChatService);

        TaskPlan plan = new TaskPlan("p1", List.of(
                new TaskStep(1, IntentType.ORDER_STATUS, Map.of("order_id", "123"), false, "请查询订单123状态。"),
                new TaskStep(2, IntentType.CART_MANAGEMENT, Map.of("dish", "鱼香肉丝"), false, "请把鱼香肉丝加入购物车。")
        ));
        when(agentChatService.askStep(eq("请查询订单123状态。"), any(), any(), any())).thenReturn("订单123已送达");
        when(agentChatService.askStep(eq("请把鱼香肉丝加入购物车。"), any(), any(), any())).thenReturn("已加入购物车");

        TaskExecutionOutcome outcome = service.executePlan("原始问题", "c1", "u1", mock(IntentRecognitionResult.class), plan);

        assertTrue(outcome.completed());
        verify(agentChatService).askStep(eq("请查询订单123状态。"), eq("c1"), eq("u1"), any());
        verify(agentChatService).askStep(eq("请把鱼香肉丝加入购物车。"), eq("c1"), eq("u1"), any());
    }

    @Test
    void executePlanShouldRunBatchCancelStepsSequentially() {
        TaskPlanner planner = mock(TaskPlanner.class);
        TaskExecutionStateRepository repository = mock(TaskExecutionStateRepository.class);
        AgentChatService agentChatService = mock(AgentChatService.class);
        TaskOrchestratorService service = new TaskOrchestratorService(planner, repository, agentChatService);

        TaskPlan plan = new TaskPlan("p-batch", List.of(
                new TaskStep(1, IntentType.CANCEL_ORDER, Map.of("order_id", "1779351452612"), false, "请取消目标订单。 订单号：1779351452612。"),
                new TaskStep(2, IntentType.CANCEL_ORDER, Map.of("order_id", "1779341664613"), false, "请取消目标订单。 订单号：1779341664613。")
        ));
        when(agentChatService.askStep(eq("请取消目标订单。 订单号：1779351452612。"), any(), any(), any())).thenReturn("已取消订单1779351452612");
        when(agentChatService.askStep(eq("请取消目标订单。 订单号：1779341664613。"), any(), any(), any())).thenReturn("已取消订单1779341664613");

        TaskExecutionOutcome outcome = service.executePlan("取消这两个订单吧", "c-batch", "u-batch", mock(IntentRecognitionResult.class), plan);

        assertTrue(outcome.completed());
        assertEquals(2, outcome.stepSummaries().size());
        verify(agentChatService).askStep(eq("请取消目标订单。 订单号：1779351452612。"), eq("c-batch"), eq("u-batch"), any());
        verify(agentChatService).askStep(eq("请取消目标订单。 订单号：1779341664613。"), eq("c-batch"), eq("u-batch"), any());
    }

    @Test
    void executePlanShouldCaptureQueryOutputsBeforeBatchCancelConfirmation() {
        TaskPlanner planner = mock(TaskPlanner.class);
        TaskExecutionStateRepository repository = mock(TaskExecutionStateRepository.class);
        AgentChatService agentChatService = mock(AgentChatService.class);
        TaskOrchestratorService service = new TaskOrchestratorService(planner, repository, agentChatService);

        AtomicReference<TaskExecutionState> savedState = new AtomicReference<>();
        when(repository.findByConversationId("c-query")).thenAnswer(invocation -> savedState.get());
        doAnswer(invocation -> {
            savedState.set(invocation.getArgument(0));
            return null;
        }).when(repository).save(any());

        TaskPlan plan = new TaskPlan("p-query", List.of(
                new TaskStep(1, IntentType.ORDER_STATUS, Map.of("query_mode", "recent_orders", "order_count", "2", "order_status", "not_delivered"), false, "请先查询最近的 2 个符合条件的订单，只返回 JSON：{\"order_ids\":\"id1,id2\"}。"),
                new TaskStep(2, IntentType.CANCEL_ORDER, Map.of("target_order_slot", "order_id_1"), true, "请取消第一个目标订单。"),
                new TaskStep(3, IntentType.CANCEL_ORDER, Map.of("target_order_slot", "order_id_2"), false, "请取消第二个目标订单。")
        ));
        when(agentChatService.askStep(eq("请先查询最近的 2 个符合条件的订单，只返回 JSON：{\"order_ids\":\"id1,id2\"}。"), any(), any(), any()))
                .thenReturn("{\"order_ids\":\"1779351452612,1779341664613\"}");
        when(agentChatService.askStep(eq("请取消第一个目标订单。"), any(), any(), any()))
                .thenReturn("已取消订单1779351452612");
        when(agentChatService.askStep(eq("请取消第二个目标订单。"), any(), any(), any()))
                .thenReturn("已取消订单1779341664613");

        TaskExecutionOutcome outcome = service.executePlan("帮我看看最近的两个没有送到的订单，给我退掉", "c-query", "u-query", mock(IntentRecognitionResult.class), plan);

        assertFalse(outcome.completed());
        assertTrue(outcome.waitingForConfirmation());
        assertEquals(1, outcome.stepSummaries().size());
        assertTrue(savedState.get().stepOutputs().containsKey("order_id_1"));
        assertTrue(savedState.get().stepOutputs().containsKey("order_id_2"));

        TaskExecutionOutcome completed = service.continueAfterConfirmation("c-query", "u-query");

        assertTrue(completed.completed());
        assertEquals(2, completed.stepSummaries().size());
        verify(agentChatService).askStep(eq("请先查询最近的 2 个符合条件的订单，只返回 JSON：{\"order_ids\":\"id1,id2\"}。"), eq("c-query"), eq("u-query"), any());
        verify(agentChatService).askStep(eq("请取消第一个目标订单。"), eq("c-query"), eq("u-query"), any());
        verify(agentChatService).askStep(eq("请取消第二个目标订单。"), eq("c-query"), eq("u-query"), any());
    }

    @Test
    void continueAfterConfirmationShouldRejectMismatchedUser() {
        TaskPlanner planner = mock(TaskPlanner.class);
        TaskExecutionStateRepository repository = mock(TaskExecutionStateRepository.class);
        AgentChatService agentChatService = mock(AgentChatService.class);
        TaskOrchestratorService service = new TaskOrchestratorService(planner, repository, agentChatService);

        TaskPlan plan = new TaskPlan("p2", List.of(
                new TaskStep(1, IntentType.CANCEL_ORDER, Map.of("order_id", "456"), true, "请取消订单456。")
        ));
        TaskExecutionState state = TaskExecutionState.start("c2", "owner-user", "取消并加购", plan).withWaitingConfirmation(0);
        when(repository.findByConversationId("c2")).thenReturn(state);

        TaskExecutionOutcome outcome = service.continueAfterConfirmation("c2", "other-user");

        assertFalse(outcome.completed());
        assertFalse(outcome.waitingForConfirmation());
        verify(agentChatService, never()).askStep(any(), any(), any(), any());
    }
}
