package com.weiqiang.skyai.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiqiang.skyai.intent_recognition.model.ConfidenceLevel;
import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionResult;
import com.weiqiang.skyai.intent_recognition.model.IntentType;
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
    private static final long MIN_PLAN_TIMEOUT_SECONDS = 45L;
    private static final long MAX_PLAN_TIMEOUT_SECONDS = 180L;
    private static final long BASE_PLAN_TIMEOUT_SECONDS = 30L;
    private static final long STEP_TIMEOUT_SECONDS = 20L;
    private static final long HIGH_RISK_STEP_TIMEOUT_SECONDS = 15L;

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
        TaskExecutionState state = startExecutionState(conversationId, userId, question, plan);
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
        TaskExecutionState normalized = ensureTiming(state);
        if (normalized != state) {
            stateRepository.save(normalized);
        }
        return continueExecution(normalized, true);
    }

    public void abandon(String conversationId) {
        log.info("Plan abandoned conversationId={}", conversationId);
        stateRepository.deleteByConversationId(conversationId);
    }

    private TaskExecutionOutcome continueExecution(TaskExecutionState state, boolean confirmationGranted) {
        List<String> summaries = new ArrayList<>();
        TaskExecutionState current = ensureTiming(state);
        if (current != state) {
            stateRepository.save(current);
        }
        List<TaskStep> steps = current.plan().steps();
        int index = current.nextStepIndex();
        log.info("Continuing execution conversationId={} from stepIndex={} totalSteps={}", current.conversationId(), index, steps.size());
        while (index < steps.size()) {
            String timeoutError = timeoutMessageIfExpired(current);
            if (StringUtils.hasText(timeoutError)) {
                log.warn("Plan timeout conversationId={} stepIndex={} deadlineAt={} now={}",
                        current.conversationId(), index, current.deadlineAtMillis(), System.currentTimeMillis());
                return failExecution(current, summaries, timeoutError);
            }
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
            String answer;
            try {
                answer = agentChatService.askStep(stepPrompt(step, current), current.conversationId(), current.userId(), stepIntent);
            } catch (Exception ex) {
                log.warn("Step {} failed conversationId={} intent={}", step.stepNumber(), current.conversationId(), step.intent().value(), ex);
                return failExecution(current, summaries, "步骤 " + step.stepNumber() + " 执行失败：" + safeMessage(ex));
            }
            summaries.add(answer);

            Map<String, String> stepOutputs = new LinkedHashMap<>();
            if (StringUtils.hasText(answer)) {
                stepOutputs.put("step_" + step.stepNumber() + "_answer", answer);
                stepOutputs.putAll(extractOrderOutputs(answer));
            }
            String validationError = validateStepOutcome(step, current, stepOutputs);
            if (StringUtils.hasText(validationError)) {
                log.warn("Step {} validation failed conversationId={} reason={}", step.stepNumber(), current.conversationId(), validationError);
                return failExecution(current.afterStep(index, stepOutputs), summaries, validationError);
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
        String resolvedOrderId = resolveTargetOrderId(step, current);
        if (StringUtils.hasText(resolvedOrderId)) {
            mergedEntities.put("order_id", resolvedOrderId);
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

    private String validateStepOutcome(TaskStep step, TaskExecutionState current, Map<String, String> stepOutputs) {
        String targetOrderSlot = step.entities() == null ? null : step.entities().get("target_order_slot");
        if (StringUtils.hasText(targetOrderSlot)) {
            String resolvedOrderId = resolveTargetOrderId(step, current);
            if (!StringUtils.hasText(resolvedOrderId)) {
                return "无法解析步骤 " + step.stepNumber() + " 需要的订单ID：" + targetOrderSlot;
            }
        }

        if (step.intent() == IntentType.ORDER_STATUS && isRecentOrderLookup(step)) {
            int expectedCount = parseExpectedLookupCount(step);
            List<String> resolvedOrderIds = orderedLookupOrderIds(stepOutputs);
            if (expectedCount > 0 && resolvedOrderIds.size() < expectedCount) {
                return "订单查询结果不足，期望 " + expectedCount + " 个订单ID，但只得到 " + resolvedOrderIds.size() + " 个";
            }
        }

        return null;
    }

    private boolean isRecentOrderLookup(TaskStep step) {
        return step.entities() != null && "recent_orders".equals(step.entities().get("query_mode"));
    }

    private int parseExpectedLookupCount(TaskStep step) {
        if (step.entities() == null) {
            return -1;
        }
        String orderCount = step.entities().get("order_count");
        if (!StringUtils.hasText(orderCount)) {
            return -1;
        }
        try {
            return Integer.parseInt(orderCount.trim());
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private List<String> orderedLookupOrderIds(Map<String, String> stepOutputs) {
        List<String> ordered = new ArrayList<>();
        if (stepOutputs == null || stepOutputs.isEmpty()) {
            return ordered;
        }
        String orderIds = stepOutputs.get("order_ids");
        if (StringUtils.hasText(orderIds)) {
            for (String part : orderIds.split("[,，;；\\s]+")) {
                if (StringUtils.hasText(part)) {
                    ordered.add(part.trim());
                }
            }
        }
        for (int i = 1; i <= 3; i++) {
            String key = "order_id_" + i;
            String value = stepOutputs.get(key);
            if (StringUtils.hasText(value) && !ordered.contains(value.trim())) {
                ordered.add(value.trim());
            }
        }
        String orderId = stepOutputs.get("order_id");
        if (StringUtils.hasText(orderId) && !ordered.contains(orderId.trim())) {
            ordered.add(orderId.trim());
        }
        return ordered;
    }

    private String resolveTargetOrderId(TaskStep step, TaskExecutionState current) {
        if (step == null || step.entities() == null || current == null || current.stepOutputs() == null) {
            return null;
        }
        String targetOrderSlot = step.entities().get("target_order_slot");
        if (!StringUtils.hasText(targetOrderSlot)) {
            return null;
        }
        String orderId = current.stepOutputs().get(targetOrderSlot);
        if (StringUtils.hasText(orderId)) {
            return orderId.trim();
        }
        if ("order_id".equals(targetOrderSlot)) {
            String primaryOrderId = current.stepOutputs().get("order_id");
            if (StringUtils.hasText(primaryOrderId)) {
                return primaryOrderId.trim();
            }
        }
        return null;
    }

    private TaskExecutionOutcome failExecution(TaskExecutionState current, List<String> summaries, String message) {
        stateRepository.deleteByConversationId(current.conversationId());
        return new TaskExecutionOutcome(false, false, StringUtils.hasText(message) ? message : "任务执行失败", current, null, summaries);
    }

    private TaskExecutionState startExecutionState(String conversationId,
                                                   String userId,
                                                   String question,
                                                   TaskPlan plan) {
        long startedAtMillis = System.currentTimeMillis();
        long deadlineAtMillis = startedAtMillis + buildPlanTimeoutMillis(plan);
        return TaskExecutionState.start(conversationId, userId, question, plan, startedAtMillis, deadlineAtMillis);
    }

    private TaskExecutionState ensureTiming(TaskExecutionState state) {
        if (state == null) {
            return null;
        }
        if (state.startedAtMillis() != null && state.deadlineAtMillis() != null) {
            return state;
        }
        long startedAtMillis = state.startedAtMillis() != null ? state.startedAtMillis() : System.currentTimeMillis();
        long deadlineAtMillis = state.deadlineAtMillis() != null
                ? state.deadlineAtMillis()
                : startedAtMillis + buildPlanTimeoutMillis(state.plan());
        return state.withTiming(startedAtMillis, deadlineAtMillis);
    }

    private long buildPlanTimeoutMillis(TaskPlan plan) {
        if (plan == null || plan.steps() == null || plan.steps().isEmpty()) {
            return MIN_PLAN_TIMEOUT_SECONDS * 1000L;
        }
        int stepCount = plan.steps().size();
        long highRiskSteps = plan.steps().stream().filter(TaskStep::requiresConfirmation).count();
        long budgetSeconds = BASE_PLAN_TIMEOUT_SECONDS
                + STEP_TIMEOUT_SECONDS * stepCount
                + HIGH_RISK_STEP_TIMEOUT_SECONDS * highRiskSteps;
        long clampedSeconds = Math.max(MIN_PLAN_TIMEOUT_SECONDS, Math.min(MAX_PLAN_TIMEOUT_SECONDS, budgetSeconds));
        return clampedSeconds * 1000L;
    }

    private String timeoutMessageIfExpired(TaskExecutionState state) {
        if (state == null || state.deadlineAtMillis() == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (now <= state.deadlineAtMillis()) {
            return null;
        }
        return "任务执行超时，请缩短步骤数量或拆分后再试。";
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

    private String safeMessage(Throwable ex) {
        if (ex == null || !StringUtils.hasText(ex.getMessage())) {
            return "未知错误";
        }
        return ex.getMessage();
    }

    private String answerPreview(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.length() <= 120 ? trimmed : trimmed.substring(0, 120);
    }
}
