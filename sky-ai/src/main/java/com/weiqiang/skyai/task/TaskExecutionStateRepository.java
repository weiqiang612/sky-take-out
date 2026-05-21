package com.weiqiang.skyai.task;

import com.weiqiang.skyai.task.model.TaskExecutionState;

public interface TaskExecutionStateRepository {
    void save(TaskExecutionState state);

    TaskExecutionState findByConversationId(String conversationId);

    void deleteByConversationId(String conversationId);
}
