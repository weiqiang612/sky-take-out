package com.weiqiang.skyai.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiqiang.skyai.intent_recognition.model.ConfidenceLevel;
import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionResult;
import com.weiqiang.skyai.task.model.TaskExecutionOutcome;
import com.weiqiang.skyai.task.model.TaskExecutionState;
import com.weiqiang.skyai.task.model.TaskPlan;
import com.weiqiang.skyai.task.model.TaskPlanningResult;
import com.weiqiang.skyai.task.model.TaskStep;
import com.weiqiang.skyai.websocket.AgentChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class TaskOrchestratorService {

    private static final ObjectMapper ORDER_OUTPUT_MAPPER = new ObjectMapper();

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
        TaskPlanningResult result = taskPlanner.plan(question, preIntent, List.of());
        log.info("Plan created for conversationId={}: decomposed={}, steps={}", conversationId, result.decomposed(),
                result.decomposed() ? result.plan().steps().size() : 0);
        return result;
    }

    public TaskExecutionOutcome executePlan(String question,
                                            String conversationId,
                                            String userId,
                                            IntentRecognitionResult preIntent,
                                            TaskPlan plan) {
        log.info("Starting plan execution conversationId={} userId={} question={} steps={}", conversationId, userId, question, plan.steps().size());
        TaskExecutionState state = TaskExecutionState.start(conversationId, userId, question, plan);
        stateRepository.save(state);
        // 直接执行，直到需要用户确认或者执行完成
        return continueExecution(state, false);
    }

    public TaskExecutionOutcome continueAfterConfirmation(String conversationId, String userId) {
        log.info("Confirmation received conversationId={} userId={}", conversationId, userId);
        TaskExecutionState state = stateRepository.findByConversationId(conversationId);
        if (state == null) {
            log.warn("No state found for conversationId={}", conversationId);
            return new TaskExecutionOutcome(false, false, "", null, null, List.of());
        }
        if (!StringUtils.hasText(userId) || !userId.equals(state.userId())) {
            log.warn("UserId mismatch: provided={}, expected={}", userId, state.userId());
            return new TaskExecutionOutcome(false, false, "", null, null, List.of());
        }
        Integer waitingStep = state.waitingConfirmationStep();
        if (waitingStep == null || waitingStep < 0 || waitingStep >= state.plan().steps().size()) {
            log.warn("Invalid waitingConfirmationStep={} for conversationId={}", waitingStep, conversationId);
            return new TaskExecutionOutcome(false, false, "", null, null, List.of());
        }
        return continueExecution(state, true);
    }

    public void abandon(String conversationId) {
        log.info("Plan abandoned conversationId={}", conversationId);
        stateRepository.deleteByConversationId(conversationId);
    }

    private TaskExecutionOutcome continueExecution(TaskExecutionState state, boolean confirmationGranted) {
        List<String> summaries = new ArrayList<>();
        TaskExecutionState current = state;
        List<TaskStep> steps = current.plan().steps();
        int index = current.nextStepIndex();
        log.info("Continuing execution conversationId={} from stepIndex={} totalSteps={}", current.conversationId(), index, steps.size());
        while (index < steps.size()) {
            TaskStep step = steps.get(index);

            if (step.requiresConfirmation()) {
                log.info("Step {} requires confirmation, suspending execution", step.stepNumber());
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

            log.info("Executing step {}/{} intent={}", step.stepNumber(), steps.size(), step.intent().value());
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
                stepOutputs.putAll(extractOrderOutputs(answer));
            }
            // 更新状态，继续执行下一步
            current = current.afterStep(index, stepOutputs);
            stateRepository.save(current);
            confirmationGranted = false;
            index = current.nextStepIndex();
        }

        log.info("All {} steps completed conversationId={}", steps.size(), current.conversationId());
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
        if (step.entities() != null) {
            mergedEntities.putAll(step.entities());
        }
        if (current.stepOutputs() != null) {
            mergedEntities.putAll(current.stepOutputs());
        }
        log.debug("Merged entities for step {}: {}", step.stepNumber(), mergedEntities);
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

    private Map<String, String> extractOrderOutputs(String answer) {
        Map<String, String> outputs = new LinkedHashMap<>();
        if (!StringUtils.hasText(answer)) {
            return outputs;
        }

        JsonNode root = readJson(answer);
        if (root == null || root.isMissingNode() || root.isNull()) {
            return outputs;
        }

        List<String> orderIds = new ArrayList<>();
        if (root.hasNonNull("order_ids")) {
            JsonNode orderIdsNode = root.get("order_ids");
            if (orderIdsNode.isArray()) {
                orderIdsNode.forEach(node -> addOrderId(orderIds, node.asText(null)));
            } else {
                String value = orderIdsNode.asText(null);
                if (StringUtils.hasText(value)) {
                    for (String part : value.split("[,，;；\\s]+")) {
                        addOrderId(orderIds, part);
                    }
                }
            }
        }
        if (root.hasNonNull("order_id")) {
            addOrderId(orderIds, root.get("order_id").asText(null));
        }
        for (int i = 1; i <= 3; i++) {
            String key = "order_id_" + i;
            if (root.hasNonNull(key)) {
                addOrderId(orderIds, root.get(key).asText(null));
            }
        }

        if (orderIds.isEmpty()) {
            return outputs;
        }

        LinkedHashSet<String> unique = new LinkedHashSet<>(orderIds);
        List<String> canonical = new ArrayList<>(unique);
        outputs.put("order_ids", String.join(",", canonical));
        outputs.put("order_id", canonical.get(0));
        for (int i = 0; i < canonical.size() && i < 3; i++) {
            outputs.put("order_id_" + (i + 1), canonical.get(i));
        }
        return outputs;
    }

    private JsonNode readJson(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            String candidate = text.trim();
            int firstBrace = candidate.indexOf('{');
            int lastBrace = candidate.lastIndexOf('}');
            if (firstBrace >= 0 && lastBrace > firstBrace) {
                candidate = candidate.substring(firstBrace, lastBrace + 1);
            }
            return ORDER_OUTPUT_MAPPER.readTree(candidate);
        } catch (Exception ex) {
            log.debug("Unable to parse structured step output: {}", answerPreview(text));
            return null;
        }
    }

    private void addOrderId(List<String> orderIds, String orderId) {
        if (StringUtils.hasText(orderId)) {
            orderIds.add(orderId.trim());
        }
    }

    private String answerPreview(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.length() <= 120 ? trimmed : trimmed.substring(0, 120);
    }
}
