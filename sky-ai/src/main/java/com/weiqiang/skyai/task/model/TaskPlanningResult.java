package com.weiqiang.skyai.task.model;

public record TaskPlanningResult(
        boolean decomposed,
        TaskPlan plan
) {
    // 如果没有被分解，plan应该为null
    public static TaskPlanningResult notDecomposed() {
        return new TaskPlanningResult(false, null);
    }
}
