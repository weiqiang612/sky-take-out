package com.weiqiang.skyai.controller;

import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionResult;
import com.weiqiang.skyai.intent_recognition.model.IntentType;
import com.weiqiang.skyai.task.TaskOrchestratorService;
import com.weiqiang.skyai.task.model.TaskExecutionOutcome;
import com.weiqiang.skyai.task.model.TaskPlanningResult;
import com.weiqiang.skyai.websocket.AgentChatService;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.weiqiang.skyai.annotation.RateLimit;

import java.util.Map;

@RestController
@RequestMapping("/ai")
public class ChatController {

    private final AgentChatService agentChatService;
    private final TaskOrchestratorService taskOrchestratorService;

    public ChatController(AgentChatService agentChatService, TaskOrchestratorService taskOrchestratorService) {
        this.agentChatService = agentChatService;
        this.taskOrchestratorService = taskOrchestratorService;
    }

    @RateLimit
    @GetMapping("/ask")
    public Map<String, String> ask(@RequestParam("question") String question,
                                   @RequestParam(value = "conversationId", defaultValue = ChatMemory.DEFAULT_CONVERSATION_ID) String conversationId,
                                   @RequestParam(value = "userId", defaultValue = "anonymous") String userId) {
        IntentRecognitionResult preIntent = agentChatService.recognizeIntent(question, conversationId, userId);

        if (preIntent.intent() == IntentType.OTHER) {
            return Map.of("question", question, "answer", agentChatService.otherIntentResponse(preIntent));
        }

        if (preIntent.requiresHumanConfirmation()) {
            return Map.of("question", question, "answer", agentChatService.confirmationQuestion(preIntent));
        }

        TaskPlanningResult planningResult = taskOrchestratorService.plan(question, conversationId, userId, preIntent);
        if (planningResult.decomposed() && planningResult.plan() != null) {
            TaskExecutionOutcome outcome =
                    taskOrchestratorService.executePlan(question, conversationId, userId, preIntent, planningResult.plan());
            if (outcome.completed()) {
                return Map.of("question", question, "answer", outcome.finalAnswer());
            }
            if (outcome.waitingForConfirmation() && outcome.waitingStep() != null) {
                int waitingStep = outcome.waitingStep();
                String waitingMessage = "下一步需要确认后继续执行：步骤 " + waitingStep;
                return Map.of("question", question, "answer", waitingMessage);
            }
        }

        return Map.of("question", question, "answer", agentChatService.ask(question, conversationId, userId, preIntent));
    }

    private boolean shouldUseRag(IntentRecognitionResult intentResult) {
        return intentResult != null && intentResult.intent() == IntentType.FAQ;
    }
}
