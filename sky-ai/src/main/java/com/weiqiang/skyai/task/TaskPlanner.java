package com.weiqiang.skyai.task;

import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionResult;
import com.weiqiang.skyai.task.model.TaskPlanningResult;

import java.util.List;

public interface TaskPlanner {
    TaskPlanningResult plan(String question, IntentRecognitionResult recognizedIntent, List<String> history);
}
