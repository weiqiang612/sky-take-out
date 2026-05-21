package com.weiqiang.skyai.task.model;

import java.util.List;

public record TaskExecutionOutcome(
        boolean completed,
        boolean waitingForConfirmation,
        String finalAnswer,
        TaskExecutionState state,
        Integer waitingStep,
        List<String> stepSummaries
) {
}
