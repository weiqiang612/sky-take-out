package com.weiqiang.skyai.task.model;

import java.util.LinkedHashMap;
import java.util.Map;

public record TaskExecutionState(
        String planId,
        String conversationId,
        String userId,
        String originalQuestion,
        TaskPlan plan,
        int nextStepIndex,
        Integer waitingConfirmationStep,
        Map<String, String> stepOutputs
) {
    public static TaskExecutionState start(String conversationId,
                                           String userId,
                                           String originalQuestion,
                                           TaskPlan plan) {
        return new TaskExecutionState(
                plan.planId(),
                conversationId,
                userId,
                originalQuestion,
                plan,
                0,
                null,
                new LinkedHashMap<>()
        );
    }

    public TaskExecutionState withWaitingConfirmation(int stepIndex) {
        return new TaskExecutionState(
                planId,
                conversationId,
                userId,
                originalQuestion,
                plan,
                nextStepIndex,
                stepIndex,
                new LinkedHashMap<>(stepOutputs)
        );
    }

    public TaskExecutionState afterStep(int finishedStepIndex, Map<String, String> appendedOutputs) {
        Map<String, String> merged = new LinkedHashMap<>(stepOutputs);
        if (appendedOutputs != null) {
            merged.putAll(appendedOutputs);
        }
        return new TaskExecutionState(
                planId,
                conversationId,
                userId,
                originalQuestion,
                plan,
                finishedStepIndex + 1,
                null,
                merged
        );
    }
}
