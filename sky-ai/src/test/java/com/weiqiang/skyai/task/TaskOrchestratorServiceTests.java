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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
