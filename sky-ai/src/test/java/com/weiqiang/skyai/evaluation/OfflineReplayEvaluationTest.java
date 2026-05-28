package com.weiqiang.skyai.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiqiang.skyai.intent_recognition.model.ConfidenceLevel;
import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionResult;
import com.weiqiang.skyai.intent_recognition.model.IntentType;
import com.weiqiang.skyai.memory.model.MemoryFactKey;
import com.weiqiang.skyai.memory.model.MemoryFactSourceType;
import com.weiqiang.skyai.memory.repository.RedisChatMemoryRepository;
import com.weiqiang.skyai.memory.service.MemoryWriterService;
import com.weiqiang.skyai.memory.service.UserMemoryFactService;
import com.weiqiang.skyai.task.RuleBasedTaskPlanner;
import com.weiqiang.skyai.task.TaskExecutionStateRepository;
import com.weiqiang.skyai.task.TaskOrchestratorService;
import com.weiqiang.skyai.task.TaskPlanner;
import com.weiqiang.skyai.task.model.TaskExecutionOutcome;
import com.weiqiang.skyai.task.model.TaskExecutionState;
import com.weiqiang.skyai.task.model.TaskPlan;
import com.weiqiang.skyai.task.model.TaskPlanningResult;
import com.weiqiang.skyai.task.model.TaskStep;
import com.weiqiang.skyai.websocket.AgentChatService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OfflineReplayEvaluationTest {

    private static final Path REPORT_DIR = Path.of("target", "offline-replay");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void generateReplayReport() throws Exception {
        List<ReplayCaseResult> results = new ArrayList<>();
        results.addAll(runPlannerSuite());
        results.addAll(runOrchestratorSuite());
        results.addAll(runMemorySuite());

        writeReport(results);
        assertTrue(Files.exists(REPORT_DIR.resolve("replay-report.json")));
        assertTrue(Files.exists(REPORT_DIR.resolve("replay-report.md")));
        System.out.println("Replay report written to " + REPORT_DIR.toAbsolutePath());
    }

    private List<ReplayCaseResult> runPlannerSuite() {
        RuleBasedTaskPlanner planner = new RuleBasedTaskPlanner();
        List<ReplayCase> cases = List.of(
                replayCase("batch-cancel-2", "取消这两个订单吧", intent(
                        IntentType.CANCEL_ORDER, Map.of("order_ids", "1779351452612,1779341664613"),
                        List.of(IntentType.CANCEL_ORDER), false), true, 2),
                replayCase("batch-cancel-3", "帮我取消这三个订单", intent(
                        IntentType.CANCEL_ORDER, Map.of("order_ids", "1779351452612,1779341664613,1779341664614"),
                        List.of(IntentType.CANCEL_ORDER), false), true, 3),
                replayCase("lookup-cancel-2", "帮我看看最近的两个没有送到的订单，给我退掉", intent(
                        IntentType.CANCEL_ORDER, Map.of("order_count", "2", "order_status", "not_delivered"),
                        List.of(IntentType.ORDER_STATUS, IntentType.CANCEL_ORDER), false), true, 3),
                replayCase("lookup-fallback-multi-intent", "帮我看看最近的两个订单，给我退掉", intent(
                        IntentType.CANCEL_ORDER, Map.of("order_count", "2"),
                        List.of(IntentType.ORDER_STATUS, IntentType.CANCEL_ORDER), false), true, 2),
                replayCase("multi-intent", "查一下再退款然后取消", intent(
                        IntentType.CANCEL_ORDER, Map.of(),
                        List.of(IntentType.REQUEST_REFUND, IntentType.ORDER_STATUS, IntentType.CANCEL_ORDER), false), true, 3),
                replayCase("single-cancel", "取消这个订单吧", intent(
                        IntentType.CANCEL_ORDER, Map.of("order_id", "1779351452612"),
                        List.of(IntentType.CANCEL_ORDER), false), false, 0),
                replayCase("batch-over-limit", "取消这四个订单", intent(
                        IntentType.CANCEL_ORDER, Map.of("order_ids", "1,2,3,4"),
                        List.of(IntentType.CANCEL_ORDER), false), false, 0),
                replayCase("lookup-over-limit", "帮我看看最近的四个没有送到的订单，给我退掉", intent(
                        IntentType.CANCEL_ORDER, Map.of("order_count", "4", "order_status", "not_delivered"),
                        List.of(IntentType.ORDER_STATUS, IntentType.CANCEL_ORDER), false), false, 0)
        );
        List<ReplayCaseResult> results = new ArrayList<>();
        for (ReplayCase replayCase : cases) {
            long start = System.nanoTime();
            TaskPlanningResult result = planner.plan(replayCase.question(), replayCase.intent(), List.of());
            long latency = millisSince(start);
            boolean pass = result.decomposed() == replayCase.expectedDecomposed();
            if (replayCase.expectedStepCount() > 0) {
                pass = pass && result.plan() != null && result.plan().steps().size() == replayCase.expectedStepCount();
            } else {
                pass = pass && result.plan() == null;
            }
            String detail = result.decomposed()
                    ? result.plan().steps().stream().map(step -> step.intent().value()).reduce((a, b) -> a + "|" + b).orElse("")
                    : "not decomposed";
            results.add(new ReplayCaseResult("planner", replayCase.name(), pass, latency, detail));
        }
        return results;
    }

    private List<ReplayCaseResult> runOrchestratorSuite() {
        List<ReplayCaseResult> results = new ArrayList<>();
        results.add(runOrchestratorCase("simple-two-step",
                List.of(
                        step(1, IntentType.ORDER_STATUS, Map.of("order_id", "123"), false, "请查询订单123状态。"),
                        step(2, IntentType.REQUEST_REFUND, Map.of("order_id", "123"), false, "请为订单123发起退款。")
                ),
                Map.<String, Object>of(
                        "请查询订单123状态。", "订单123已送达",
                        "请为订单123发起退款。", "Refund issued for order 123: late delivery"
                ),
                state -> state.completed() && state.stepSummaries().size() == 2));

        results.add(runOrchestratorCase("confirmation-flow",
                List.of(
                        step(1, IntentType.ORDER_STATUS, Map.of("query_mode", "recent_orders", "order_count", "2", "order_status", "not_delivered"), false, "请先查询最近的 2 个符合条件的订单，只返回 JSON：{\"order_ids\":\"id1,id2\"}。"),
                        step(2, IntentType.CANCEL_ORDER, Map.of("target_order_slot", "order_id_1"), true, "请取消第一个目标订单。"),
                        step(3, IntentType.CANCEL_ORDER, Map.of("target_order_slot", "order_id_2"), false, "请取消第二个目标订单。")
                ),
                Map.<String, Object>of(
                        "请先查询最近的 2 个符合条件的订单，只返回 JSON：{\"order_ids\":\"id1,id2\"}。", "{\"order_ids\":\"1779351452612,1779341664613\"}",
                        "请取消第一个目标订单。", "已取消订单1779351452612",
                        "请取消第二个目标订单。", "已取消订单1779341664613"
                ),
                outcome -> outcome.completed() && outcome.stepSummaries().size() == 2));

        results.add(runOrchestratorCase("step-failure",
                List.of(
                        step(1, IntentType.ORDER_STATUS, Map.of("order_id", "123"), false, "请查询订单123状态。"),
                        step(2, IntentType.CANCEL_ORDER, Map.of("order_id", "123"), false, "请取消订单123。")
                ),
                Map.<String, Object>of("请查询订单123状态。", "订单123已送达", "请取消订单123。", new IllegalStateException("cancel failed")),
                outcome -> !outcome.completed() && outcome.finalAnswer().contains("步骤 2 执行失败")));

        results.add(runTimeoutCase());
        return results;
    }

    private ReplayCaseResult runOrchestratorCase(String name,
                                                 List<TaskStep> steps,
                                                 Map<String, Object> answers,
                                                 java.util.function.Predicate<TaskExecutionOutcome> assertion) {
        AgentChatService agentChatService = mock(AgentChatService.class);
        TaskOrchestratorService service = new TaskOrchestratorService((q, i, h) -> TaskPlanningResult.notDecomposed(),
                new InMemoryTaskExecutionStateRepository(), agentChatService);
        when(agentChatService.askStep(anyString(), anyString(), anyString(), any())).thenAnswer(invocation -> {
            Object response = answers.get(invocation.getArgument(0));
            if (response instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (response instanceof Error error) {
                throw error;
            }
            return response == null ? "" : response.toString();
        });
        TaskPlan plan = new TaskPlan(name, steps);
        IntentRecognitionResult intent = intent(steps.get(0).intent(), Map.of(), List.of(steps.get(0).intent()), false);
        long start = System.nanoTime();
        TaskExecutionOutcome outcome = service.executePlan("question", name, "u1", intent, plan);
        if (outcome.waitingForConfirmation()) {
            outcome = service.continueAfterConfirmation(name, "u1");
        }
        long latency = millisSince(start);
        boolean pass = assertion.test(outcome);
        return new ReplayCaseResult("orchestrator", name, pass, latency,
                "completed=" + outcome.completed() + ", waiting=" + outcome.waitingForConfirmation());
    }

    private ReplayCaseResult runTimeoutCase() {
        AgentChatService agentChatService = mock(AgentChatService.class);
        InMemoryTaskExecutionStateRepository repository = new InMemoryTaskExecutionStateRepository();
        TaskOrchestratorService service = new TaskOrchestratorService((q, i, h) -> TaskPlanningResult.notDecomposed(),
                repository, agentChatService);
        TaskPlan plan = new TaskPlan("timeout", List.of(step(1, IntentType.CANCEL_ORDER, Map.of("order_id", "123"), true, "请取消订单123。")));
        TaskExecutionState state = TaskExecutionState.start("timeout-conv", "u1", "取消订单", plan,
                System.currentTimeMillis() - 300000, System.currentTimeMillis() - 1000).withWaitingConfirmation(0);
        repository.save(state);
        long start = System.nanoTime();
        TaskExecutionOutcome outcome = service.continueAfterConfirmation("timeout-conv", "u1");
        long latency = millisSince(start);
        boolean pass = !outcome.completed() && outcome.finalAnswer().contains("任务执行超时");
        return new ReplayCaseResult("orchestrator", "timeout", pass, latency, outcome.finalAnswer());
    }

    private List<ReplayCaseResult> runMemorySuite() throws Exception {
        List<ReplayCaseResult> results = new ArrayList<>();
        results.add(runMemoryCase("confirmation-gate", () -> {
            ChatClient.Builder chatClientBuilder = mock(ChatClient.Builder.class);
            RedisChatMemoryRepository redisRepo = mock(RedisChatMemoryRepository.class);
            UserMemoryFactService userMemoryFactService = mock(UserMemoryFactService.class);
            MemoryWriterService service = new MemoryWriterService(chatClientBuilder, redisRepo, userMemoryFactService, objectMapper);
            when(redisRepo.findByConversationId("conv-1")).thenReturn(List.of(user("请帮我取消订单")));
            service.writeTurn("u1", "conv-1", intent(IntentType.CANCEL_ORDER, Map.of(), List.of(IntentType.CANCEL_ORDER), true));
            verify(redisRepo).saveAll(anyString(), any());
            verifyNoInteractions(chatClientBuilder, userMemoryFactService);
            return "confirmation gated";
        }));

        results.add(runMemoryCase("low-confidence-skip", () -> {
            ChatClient.Builder chatClientBuilder = mock(ChatClient.Builder.class);
            RedisChatMemoryRepository redisRepo = mock(RedisChatMemoryRepository.class);
            UserMemoryFactService userMemoryFactService = mock(UserMemoryFactService.class);
            MemoryWriterService service = new MemoryWriterService(chatClientBuilder, redisRepo, userMemoryFactService, objectMapper);
            when(redisRepo.findByConversationId("conv-2")).thenReturn(List.of(user("我想问一下营业时间")));
            service.writeTurn("u1", "conv-2", intent(IntentType.FAQ, Map.of(), List.of(IntentType.FAQ), false, ConfidenceLevel.LOW));
            verify(redisRepo).saveAll(anyString(), any());
            verifyNoInteractions(chatClientBuilder, userMemoryFactService);
            return "low confidence skipped";
        }));

        results.add(runMemoryCase("cancel-tool-outcome", () -> {
            UserMemoryFactService userMemoryFactService = mock(UserMemoryFactService.class);
            MemoryWriterService service = new MemoryWriterService(mock(ChatClient.Builder.class), mock(RedisChatMemoryRepository.class), userMemoryFactService, objectMapper);
            ReflectionTestUtils.invokeMethod(service, "persistToolOutcome", "u1", IntentType.CANCEL_ORDER, "Cancelled order 88");
            String today = (String) ReflectionTestUtils.invokeMethod(service, "today");
            verify(userMemoryFactService).upsertFact("u1", MemoryFactKey.OPERATIONAL_NOTES, "已取消订单 88（" + today + "）", MemoryFactSourceType.TOOL, null);
            return "cancel note persisted";
        }));

        results.add(runMemoryCase("address-snapshot", () -> {
            UserMemoryFactService userMemoryFactService = mock(UserMemoryFactService.class);
            MemoryWriterService service = new MemoryWriterService(mock(ChatClient.Builder.class), mock(RedisChatMemoryRepository.class), userMemoryFactService, objectMapper);
            String responseData = "{\"id\":1,\"detail\":\"朝阳区建国路1号\",\"label\":\"家\"}";
            ReflectionTestUtils.invokeMethod(service, "persistToolOutcome", "u1", IntentType.ADDRESS_MANAGEMENT, responseData);
            verify(userMemoryFactService).upsertFact("u1", MemoryFactKey.DEFAULT_ADDRESS, "朝阳区建国路1号", MemoryFactSourceType.TOOL, null);
            return "default address snapshot persisted";
        }));
        return results;
    }

    private ReplayCaseResult runMemoryCase(String name, java.util.concurrent.Callable<String> action) throws Exception {
        long start = System.nanoTime();
        String detail = action.call();
        long latency = millisSince(start);
        return new ReplayCaseResult("memory", name, true, latency, detail);
    }

    private void writeReport(List<ReplayCaseResult> results) throws IOException {
        Files.createDirectories(REPORT_DIR);
        List<ReplaySuiteSummary> suites = results.stream()
                .collect(java.util.stream.Collectors.groupingBy(ReplayCaseResult::suite, LinkedHashMap::new, java.util.stream.Collectors.toList()))
                .entrySet().stream()
                .map(entry -> summary(entry.getKey(), entry.getValue()))
                .toList();
        ReplayReport report = new ReplayReport(Instant.now().toString(), suites, results);
        Files.writeString(REPORT_DIR.resolve("replay-report.json"), objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        Files.writeString(REPORT_DIR.resolve("replay-report.md"), markdown(report));
    }

    private ReplaySuiteSummary summary(String suite, List<ReplayCaseResult> results) {
        int total = results.size();
        long passed = results.stream().filter(ReplayCaseResult::passed).count();
        List<Long> latencies = results.stream().map(ReplayCaseResult::latencyMs).sorted().toList();
        long avg = Math.round(results.stream().mapToLong(ReplayCaseResult::latencyMs).average().orElse(0));
        long p95 = latencies.isEmpty() ? 0 : latencies.get(Math.min(latencies.size() - 1, (int) Math.ceil(latencies.size() * 0.95) - 1));
        return new ReplaySuiteSummary(suite, total, (int) passed, rate(passed, total), avg, p95);
    }

    private String markdown(ReplayReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Offline Replay Report\n\n");
        builder.append("| Suite | Total | Passed | Rate | Avg ms | P95 ms |\n");
        builder.append("|---|---:|---:|---:|---:|---:|\n");
        for (ReplaySuiteSummary suite : report.suites()) {
            builder.append("| ").append(suite.suite()).append(" | ").append(suite.total()).append(" | ")
                    .append(suite.passed()).append(" | ").append(String.format(Locale.ROOT, "%.1f%%", suite.rate() * 100))
                    .append(" | ").append(suite.avgLatencyMs()).append(" | ").append(suite.p95LatencyMs()).append(" |\n");
        }
        builder.append("\nGenerated at: ").append(report.generatedAt()).append('\n');
        return builder.toString();
    }

    private double rate(long passed, int total) {
        return total == 0 ? 0.0d : (double) passed / total;
    }

    private long millisSince(long startNanos) {
        return Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
    }

    private ReplayCase replayCase(String name, String question, IntentRecognitionResult intent, boolean expectedDecomposed, int expectedStepCount) {
        return new ReplayCase(name, question, intent, expectedDecomposed, expectedStepCount);
    }

    private IntentRecognitionResult intent(IntentType type, Map<String, String> entities, List<IntentType> possibleIntents, boolean confirmation) {
        return intent(type, entities, possibleIntents, confirmation, ConfidenceLevel.HIGH);
    }

    private IntentRecognitionResult intent(IntentType type, Map<String, String> entities, List<IntentType> possibleIntents, boolean confirmation, ConfidenceLevel confidence) {
        return new IntentRecognitionResult(type, confidence, entities, possibleIntents, null, confirmation, confirmation ? "please confirm" : null);
    }

    private TaskStep step(int stepNumber, IntentType intent, Map<String, String> entities, boolean confirmation, String instruction) {
        return new TaskStep(stepNumber, intent, entities, confirmation, instruction);
    }

    private Message user(String text) {
        return new UserMessage(text);
    }

    private record ReplayCase(String name, String question, IntentRecognitionResult intent, boolean expectedDecomposed, int expectedStepCount) {
    }

    private record ReplayCaseResult(String suite, String name, boolean passed, long latencyMs, String detail) {
    }

    private record ReplaySuiteSummary(String suite, int total, int passed, double rate, long avgLatencyMs, long p95LatencyMs) {
    }

    private record ReplayReport(String generatedAt, List<ReplaySuiteSummary> suites, List<ReplayCaseResult> cases) {
    }

    private static final class InMemoryTaskExecutionStateRepository implements TaskExecutionStateRepository {
        private final Map<String, TaskExecutionState> states = new LinkedHashMap<>();

        @Override
        public void save(TaskExecutionState state) {
            if (state != null) {
                states.put(state.conversationId(), state);
            }
        }

        @Override
        public TaskExecutionState findByConversationId(String conversationId) {
            return states.get(conversationId);
        }

        @Override
        public void deleteByConversationId(String conversationId) {
            states.remove(conversationId);
        }
    }
}
