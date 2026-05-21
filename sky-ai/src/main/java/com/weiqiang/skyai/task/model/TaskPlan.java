package com.weiqiang.skyai.task.model;

import java.util.List;

public record TaskPlan(
        String planId,
        List<TaskStep> steps
) {
}
