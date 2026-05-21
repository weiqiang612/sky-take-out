package com.weiqiang.skyai.task;

import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionResult;
import com.weiqiang.skyai.intent_recognition.model.IntentType;
import com.weiqiang.skyai.task.model.TaskPlan;
import com.weiqiang.skyai.task.model.TaskPlanningResult;
import com.weiqiang.skyai.task.model.TaskStep;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class RuleBasedTaskPlanner implements TaskPlanner {

    private static final int MAX_STEPS = 3;

    @Override
    public TaskPlanningResult plan(String question, IntentRecognitionResult recognizedIntent, List<String> history) {
        if (recognizedIntent == null || recognizedIntent.intent() == null) {
            return TaskPlanningResult.notDecomposed();
        }
        // 1. 不是复杂请求，不进行分步处理
        if (!isCompositeRequest(question, recognizedIntent)) {
            return TaskPlanningResult.notDecomposed();
        }

        // 2. 是复杂请求
        Set<IntentType> intents = new LinkedHashSet<>();
        // 2.1 先把当前意图加入
        if (recognizedIntent.intent().isTask()) {
            intents.add(recognizedIntent.intent());
        }
        if (recognizedIntent.possibleIntents() != null) {
            for (IntentType possible : recognizedIntent.possibleIntents()) {
                if (possible != null && possible.isTask()) {
                    // 2.2 再把候选意图加入，直到达到最大步骤数
                    intents.add(possible);
                }
                if (intents.size() >= MAX_STEPS) {
                    break;
                }
            }
        }

        if (intents.size() < 2) {
            return TaskPlanningResult.notDecomposed();
        }

        List<TaskStep> steps = new ArrayList<>();
        int stepNumber = 1;
        for (IntentType intentType : intents) {
            if (stepNumber > MAX_STEPS) {
                break;
            }
            // 对每一个意图生成步骤
            steps.add(new TaskStep(
                    stepNumber++,
                    intentType,
                    recognizedIntent.entities() == null ? Map.of() : recognizedIntent.entities(),
                    intentType.isHighRisk(),
                    // 提前生成好每个步骤的指令，避免在执行过程中再调用大模型生成，导致执行效率降低和上下文丢失
                    instructionFor(intentType)
            ));
        }

        if (steps.size() < 2) {
            return TaskPlanningResult.notDecomposed();
        }

        TaskPlan plan = new TaskPlan(UUID.randomUUID().toString(), steps);
        return new TaskPlanningResult(true, plan);
    }

    private boolean isCompositeRequest(String question, IntentRecognitionResult recognizedIntent) {
        if (!StringUtils.hasText(question)) {
            return false;
        }
        String text = question.toLowerCase(Locale.ROOT);
        // 1. 首先有明显的连接词
        boolean connectorDetected = text.contains("然后")
                || text.contains("再")
                || text.contains("并且")
                || text.contains("同时")
                || text.contains("接着")
                || text.contains(" and ")
                || text.contains(" then ")
                || text.contains(" also ");
        if (!connectorDetected) {
            return false;
        }
        return recognizedIntent.possibleIntents() != null && recognizedIntent.possibleIntents().stream()
                // 2. 其次只统计任务型意图，且去重后至少有2个，才认为是复杂请求
                .filter(it -> it != null && it.isTask())
                .distinct()
                .count() >= 2;
    }

    private String instructionFor(IntentType intentType) {
        return switch (intentType) {
            case CANCEL_ORDER -> "请取消目标订单。";
            case REQUEST_REFUND -> "请为目标订单发起退款。";
            case ORDER_STATUS -> "请查询目标订单状态。";
            case TRACK_DELIVERY -> "请查询配送进度。";
            case REPORT_MISSING_ITEM -> "请记录缺失商品并处理售后。";
            case CHANGE_ADDRESS -> "请修改该订单配送地址。";
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
}
