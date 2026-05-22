package com.weiqiang.skyai.task;

import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionResult;
import com.weiqiang.skyai.intent_recognition.model.IntentType;
import com.weiqiang.skyai.task.model.TaskPlan;
import com.weiqiang.skyai.task.model.TaskPlanningResult;
import com.weiqiang.skyai.task.model.TaskStep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
public class RuleBasedTaskPlanner implements TaskPlanner {

    private static final int MAX_STEPS = 3;

    @Override
    public TaskPlanningResult plan(String question, IntentRecognitionResult recognizedIntent, List<String> history) {
        if (recognizedIntent == null || recognizedIntent.intent() == null) {
            log.debug("Cannot plan: null intent for question={}", question);
            return TaskPlanningResult.notDecomposed();
        }

        TaskPlanningResult batchCancelPlan = buildBatchCancelPlan(recognizedIntent);
        if (batchCancelPlan.decomposed()) {
            return batchCancelPlan;
        }

        TaskPlanningResult lookupDrivenCancelPlan = buildLookupDrivenCancelPlan(question, recognizedIntent);
        if (lookupDrivenCancelPlan.decomposed()) {
            return lookupDrivenCancelPlan;
        }

        TaskPlanningResult multiIntentPlan = buildMultiIntentPlan(recognizedIntent);
        if (multiIntentPlan.decomposed()) {
            return multiIntentPlan;
        }

        log.debug("No task plan created for question={}", question);
        return TaskPlanningResult.notDecomposed();
    }

    private TaskPlanningResult buildMultiIntentPlan(IntentRecognitionResult recognizedIntent) {
        Set<IntentType> intents = new LinkedHashSet<>();
        if (recognizedIntent.intent().isTask()) {
            intents.add(recognizedIntent.intent());
        }
        if (recognizedIntent.possibleIntents() != null) {
            for (IntentType possible : recognizedIntent.possibleIntents()) {
                if (possible != null && possible.isTask()) {
                    intents.add(possible);
                }
            }
        }

        if (intents.size() < 2) {
            log.debug("Not enough task intents for multi-intent plan ({}), skipping", intents.size());
            return TaskPlanningResult.notDecomposed();
        }
        if (intents.size() > MAX_STEPS) {
            log.warn("Multi-intent plan exceeds max steps: {}", intents.size());
            return TaskPlanningResult.notDecomposed();
        }

        log.info("Composite request detected: primary={}, possibleIntents={}",
                recognizedIntent.intent(), recognizedIntent.possibleIntents());
        List<TaskStep> steps = new ArrayList<>();
        int stepNumber = 1;
        for (IntentType intentType : intents) {
            steps.add(new TaskStep(
                    stepNumber++,
                    intentType,
                    recognizedIntent.entities() == null ? Map.of() : recognizedIntent.entities(),
                    intentType.isHighRisk(),
                    instructionFor(intentType, primaryOrderReference(recognizedIntent.entities()))
            ));
        }

        TaskPlan plan = new TaskPlan(UUID.randomUUID().toString(), steps);
        log.info("Multi-intent plan created with {} steps: {}", steps.size(), steps.stream().map(s -> s.intent().value()).toList());
        return new TaskPlanningResult(true, plan);
    }

    private TaskPlanningResult buildBatchCancelPlan(IntentRecognitionResult recognizedIntent) {
        if (recognizedIntent.intent() != IntentType.CANCEL_ORDER) {
            return TaskPlanningResult.notDecomposed();
        }

        List<String> orderIds = extractOrderIds(recognizedIntent.entities());
        if (orderIds.size() < 2) {
            return TaskPlanningResult.notDecomposed();
        }
        if (orderIds.size() > MAX_STEPS) {
            log.warn("Batch cancel request exceeds max steps: {}", orderIds.size());
            return TaskPlanningResult.notDecomposed();
        }

        List<TaskStep> steps = new ArrayList<>();
        for (int i = 0; i < orderIds.size(); i++) {
            String orderId = orderIds.get(i);
            steps.add(new TaskStep(
                    i + 1,
                    IntentType.CANCEL_ORDER,
                    Map.of("order_id", orderId),
                    false,
                    instructionFor(IntentType.CANCEL_ORDER, orderId)
            ));
        }

        TaskPlan plan = new TaskPlan(UUID.randomUUID().toString(), steps);
        log.info("Batch cancel plan created with {} steps: {}", steps.size(), orderIds);
        return new TaskPlanningResult(true, plan);
    }

    private TaskPlanningResult buildLookupDrivenCancelPlan(String question, IntentRecognitionResult recognizedIntent) {
        if (recognizedIntent.intent() != IntentType.CANCEL_ORDER) {
            return TaskPlanningResult.notDecomposed();
        }

        List<String> explicitOrderIds = extractOrderIds(recognizedIntent.entities());
        if (!explicitOrderIds.isEmpty()) {
            return TaskPlanningResult.notDecomposed();
        }

        int requestedCount = parseOrderCount(recognizedIntent.entities());
        if (requestedCount != 2) {
            return TaskPlanningResult.notDecomposed();
        }
        if (!containsLookupHint(question, recognizedIntent.entities())) {
            return TaskPlanningResult.notDecomposed();
        }

        String orderStatus = orderStatusHint(recognizedIntent.entities(), question);
        if (!StringUtils.hasText(orderStatus)) {
            return TaskPlanningResult.notDecomposed();
        }

        List<TaskStep> steps = new ArrayList<>();
        steps.add(new TaskStep(
                1,
                IntentType.ORDER_STATUS,
                Map.of(
                        "query_mode", "recent_orders",
                        "order_count", String.valueOf(requestedCount),
                        "order_status", orderStatus
                ),
                false,
                "请先查询最近的 " + requestedCount + " 个符合条件的订单，只返回 JSON：{\"order_ids\":\"id1,id2\"}。"
        ));
        steps.add(new TaskStep(
                2,
                IntentType.CANCEL_ORDER,
                Map.of("target_order_slot", "order_id_1"),
                true,
                "请取消第一个目标订单。"
        ));
        steps.add(new TaskStep(
                3,
                IntentType.CANCEL_ORDER,
                Map.of("target_order_slot", "order_id_2"),
                false,
                "请取消第二个目标订单。"
        ));

        TaskPlan plan = new TaskPlan(UUID.randomUUID().toString(), steps);
        log.info("Lookup-driven cancel plan created for question={}, orderStatus={}, count={}",
                question, orderStatus, requestedCount);
        return new TaskPlanningResult(true, plan);
    }

    private String instructionFor(IntentType intentType, String orderReference) {
        return switch (intentType) {
            case CANCEL_ORDER -> appendOrderReference("请取消目标订单。", orderReference);
            case REQUEST_REFUND -> appendOrderReference("请为目标订单发起退款。", orderReference);
            case ORDER_STATUS -> "请查询目标订单状态。";
            case TRACK_DELIVERY -> "请查询配送进度。";
            case REPORT_MISSING_ITEM -> appendOrderReference("请记录缺失商品并处理售后。", orderReference);
            case CHANGE_ADDRESS -> appendOrderReference("请修改该订单配送地址。", orderReference);
            case ADDRESS_MANAGEMENT -> "请处理地址簿管理请求。";
            case MENU_QUERY -> "请查询菜品并返回可选项。";
            case CART_MANAGEMENT -> "请按用户要求处理购物车。";
            case SHOP_STATUS -> "请查询门店营业状态。";
            case REORDER -> "请基于历史订单执行再来一单。";
            case ESCALATE_TO_HUMAN -> "请执行人工转接流程。";
            case FAQ -> "请回答该问题。";
            case OTHER -> "请处理用户请求。";
        };
    }

    private String appendOrderReference(String instruction, String orderReference) {
        if (!StringUtils.hasText(orderReference) || instruction.contains(orderReference)) {
            return instruction;
        }
        return instruction + " 订单号：" + orderReference + "。";
    }

    private List<String> extractOrderIds(Map<String, String> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> ids = new LinkedHashSet<>();
        String orderIds = entities.get("order_ids");
        if (StringUtils.hasText(orderIds)) {
            for (String part : orderIds.split("[,，;；\\s]+")) {
                if (StringUtils.hasText(part)) {
                    ids.add(part.trim());
                }
            }
        }

        String orderId = entities.get("order_id");
        if (StringUtils.hasText(orderId)) {
            ids.add(orderId.trim());
        }

        List<Map.Entry<String, String>> indexed = new ArrayList<>();
        for (Map.Entry<String, String> entry : entities.entrySet()) {
            if (entry.getKey() != null && entry.getKey().matches("order_id_\\d+") && StringUtils.hasText(entry.getValue())) {
                indexed.add(entry);
            }
        }
        indexed.sort((left, right) -> Integer.compare(orderIndex(left.getKey()), orderIndex(right.getKey())));
        for (Map.Entry<String, String> entry : indexed) {
            ids.add(entry.getValue().trim());
        }

        return List.copyOf(ids);
    }

    private int parseOrderCount(Map<String, String> entities) {
        if (entities == null || entities.isEmpty()) {
            return -1;
        }
        String orderCount = entities.get("order_count");
        if (!StringUtils.hasText(orderCount)) {
            return -1;
        }
        try {
            return Integer.parseInt(orderCount.trim());
        } catch (NumberFormatException ex) {
            log.debug("Invalid order_count value: {}", orderCount);
            return -1;
        }
    }

    private boolean containsLookupHint(String question, Map<String, String> entities) {
        if (entities != null) {
            if (StringUtils.hasText(entities.get("order_status")) || StringUtils.hasText(entities.get("order_count"))) {
                return true;
            }
        }
        if (!StringUtils.hasText(question)) {
            return false;
        }
        String text = question.toLowerCase(Locale.ROOT);
        return text.contains("最近")
                || text.contains("未送达")
                || text.contains("没送到")
                || text.contains("没有送到")
                || text.contains("未到")
                || text.contains("待配送");
    }

    private String orderStatusHint(Map<String, String> entities, String question) {
        if (entities != null && StringUtils.hasText(entities.get("order_status"))) {
            return entities.get("order_status").trim();
        }
        if (!StringUtils.hasText(question)) {
            return null;
        }
        String text = question.toLowerCase(Locale.ROOT);
        if (text.contains("未送达") || text.contains("没送到") || text.contains("没有送到") || text.contains("未到")) {
            return "not_delivered";
        }
        if (text.contains("待配送")) {
            return "pending_delivery";
        }
        if (text.contains("已送达") || text.contains("送到")) {
            return "delivered";
        }
        return null;
    }

    private int orderIndex(String key) {
        if (!StringUtils.hasText(key)) {
            return Integer.MAX_VALUE;
        }
        int underscore = key.lastIndexOf('_');
        if (underscore < 0 || underscore + 1 >= key.length()) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(key.substring(underscore + 1));
        } catch (NumberFormatException ex) {
            return Integer.MAX_VALUE;
        }
    }

    private String primaryOrderReference(Map<String, String> entities) {
        List<String> orderIds = extractOrderIds(entities);
        return orderIds.isEmpty() ? null : orderIds.get(0);
    }
}
