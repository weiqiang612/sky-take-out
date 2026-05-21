package com.weiqiang.skyai.task;

import com.weiqiang.skyai.intent_recognition.model.ConfidenceLevel;
import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionResult;
import com.weiqiang.skyai.task.model.TaskExecutionOutcome;
import com.weiqiang.skyai.task.model.TaskExecutionState;
import com.weiqiang.skyai.task.model.TaskPlan;
import com.weiqiang.skyai.task.model.TaskPlanningResult;
import com.weiqiang.skyai.task.model.TaskStep;
import com.weiqiang.skyai.websocket.AgentChatService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TaskOrchestratorService {

    private final TaskPlanner taskPlanner;
    private final TaskExecutionStateRepository stateRepository;
    private final AgentChatService agentChatService;

    public TaskOrchestratorService(TaskPlanner taskPlanner,
                                   TaskExecutionStateRepository stateRepository,
                                   AgentChatService agentChatService) {
        this.taskPlanner = taskPlanner;
        this.stateRepository = stateRepository;
        this.agentChatService = agentChatService;
    }

    public TaskPlanningResult plan(String question, String conversationId, String userId, IntentRecognitionResult preIntent) {
        return taskPlanner.plan(question, preIntent, List.of());
    }

    public TaskExecutionOutcome executePlan(String question,
                                            String conversationId,
                                            String userId,
                                            IntentRecognitionResult preIntent,
                                            TaskPlan plan) {
        // 保存执行状态，默认从第0步开始执行
        TaskExecutionState state = TaskExecutionState.start(conversationId, userId, question, plan);
        stateRepository.save(state);
        // 直接执行，直到需要用户确认或者执行完成
        return continueExecution(state, false);
    }

    public TaskExecutionOutcome continueAfterConfirmation(String conversationId, String userId) {
        TaskExecutionState state = stateRepository.findByConversationId(conversationId);
        if (state == null) {
            return new TaskExecutionOutcome(false, false, "", null, null, List.of());
        }
        if (!StringUtils.hasText(userId) || !userId.equals(state.userId())) {
            return new TaskExecutionOutcome(false, false, "", null, null, List.of());
        }
        if (state.waitingConfirmationStep() == null
                || state.waitingConfirmationStep() < 0
                || state.waitingConfirmationStep() >= state.plan().steps().size()) {
            return new TaskExecutionOutcome(false, false, "", null, null, List.of());
        }
        return continueExecution(state, true);
    }

    public void abandon(String conversationId) {
        stateRepository.deleteByConversationId(conversationId);
    }

    private TaskExecutionOutcome continueExecution(TaskExecutionState state, boolean confirmationGranted) {
        // 任务执行结果摘要列表，用于最后总结输出
        List<String> summaries = new ArrayList<>();
        TaskExecutionState current = state;
        List<TaskStep> steps = current.plan().steps();
        int index = current.nextStepIndex();
        // 循环执行每一步，直到需要用户确认或者执行完成
        while (index < steps.size()) {
            TaskStep step = steps.get(index);

            // 1. 需要人工确认的步骤，先保存状态，等待用户确认后再继续执行
            if (step.requiresConfirmation()) {
                if (!confirmationGranted || current.waitingConfirmationStep() == null) {
                    TaskExecutionState waiting = current.withWaitingConfirmation(index);
                    stateRepository.save(waiting);
                    agentChatService.writeTurn(
                            waiting.userId(),
                            waiting.conversationId(),
                            new IntentRecognitionResult(
                                    step.intent(),
                                    ConfidenceLevel.HIGH,
                                    mergedEntitiesFor(step, current),
                                    List.of(step.intent()),
                                    "等待用户确认继续执行。",
                                    true,
                                    "该步骤为高风险操作，等待用户确认。"
                            )
                    );
                    // 将当前需要确认的请求和之前的步骤执行结果摘要一起返回，方便前端展示给用户
                    return new TaskExecutionOutcome(false, true, "", waiting, step.stepNumber(), summaries);
                }
            }

            // 2. 否则直接执行当前步骤，保存输出结果，继续执行下一步
            Map<String, String> mergedEntities = mergedEntitiesFor(step, current);
            IntentRecognitionResult stepIntent = new IntentRecognitionResult(
                    step.intent(),
                    ConfidenceLevel.HIGH,
                    mergedEntities,
                    List.of(step.intent()),
                    null,
                    false,
                    null
            );
            String answer = agentChatService.askStep(stepPrompt(step, current), current.conversationId(), current.userId(), stepIntent);
            summaries.add(answer);

            Map<String, String> stepOutputs = new LinkedHashMap<>();
            if (StringUtils.hasText(answer)) {
                stepOutputs.put("step_" + step.stepNumber() + "_answer", answer);
            }
            // 更新状态，继续执行下一步
            current = current.afterStep(index, stepOutputs);
            stateRepository.save(current);
            confirmationGranted = false;
            index = current.nextStepIndex();
        }

        // 所有步骤执行完成，删除状态，输出总结
        String finalAnswer = summarize(summaries);
        IntentRecognitionResult doneIntent = new IntentRecognitionResult(
                steps.get(Math.max(0, steps.size() - 1)).intent(),
                ConfidenceLevel.HIGH,
                current.stepOutputs() == null ? Map.of() : current.stepOutputs(),
                List.of(),
                null,
                false,
                null
        );
        agentChatService.writeTurn(current.userId(), current.conversationId(), doneIntent);
        stateRepository.deleteByConversationId(current.conversationId());
        return new TaskExecutionOutcome(true, false, finalAnswer, null, null, summaries);
    }

    private Map<String, String> mergedEntitiesFor(TaskStep step, TaskExecutionState current) {
        Map<String, String> mergedEntities = new LinkedHashMap<>();
        // 当前步骤的实体优先级最高，覆盖之前的同名实体
        if (step.entities() != null) {
            mergedEntities.putAll(step.entities());
        }
        if (current.stepOutputs() != null) {
            mergedEntities.putAll(current.stepOutputs());
        }
        return mergedEntities;
    }

    private String stepPrompt(TaskStep step, TaskExecutionState state) {
        if (StringUtils.hasText(step.instruction())) {
            return step.instruction();
        }
        String entities = step.entities() == null || step.entities().isEmpty() ? "" : " 实体: " + step.entities();
        return "请执行意图 " + step.intent().value() + "。" + entities;
    }

    private String summarize(List<String> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            return "";
        }
        if (summaries.size() == 1) {
            return summaries.get(0);
        }
        if (summaries.size() == 2) {
            return summaries.get(0) + "，同时" + summaries.get(1);
        }
        return "先" + summaries.get(0) + "，然后" + summaries.get(1) + "，最后" + summaries.get(2);
    }
}
