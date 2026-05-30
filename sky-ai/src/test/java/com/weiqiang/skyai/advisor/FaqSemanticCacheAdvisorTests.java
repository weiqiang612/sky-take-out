package com.weiqiang.skyai.advisor;

import com.weiqiang.skyai.intent_recognition.model.ConfidenceLevel;
import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionResult;
import com.weiqiang.skyai.intent_recognition.model.IntentType;
import com.weiqiang.skyai.rag.online.manager.FaqCacheManager;
import com.weiqiang.skyai.rag.online.model.FaqCacheItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FaqSemanticCacheAdvisorTests {

    private FaqCacheManager faqCacheManager;
    private EmbeddingModel embeddingModel;
    private FaqSemanticCacheAdvisor advisor;

    @BeforeEach
    void setUp() {
        faqCacheManager = mock(FaqCacheManager.class);
        embeddingModel = mock(EmbeddingModel.class);
        advisor = new FaqSemanticCacheAdvisor(faqCacheManager, embeddingModel);
    }

    @Test
    void shouldPassThroughWhenIntentIsNotFaq() {
        CallAdvisorChain callChain = mock(CallAdvisorChain.class);
        StreamAdvisorChain streamChain = mock(StreamAdvisorChain.class);

        // 设置意图为非 FAQ (比如 CANCEL_ORDER)
        IntentRecognitionResult intent = new IntentRecognitionResult(
                IntentType.CANCEL_ORDER, ConfidenceLevel.HIGH, Map.of(), List.of(), null, false, null
        );

        Map<String, Object> context = new HashMap<>();
        context.put("preRecognizedIntent", intent);
        ChatClientRequest request = new ChatClientRequest(new Prompt("取消我的订单"), context);

        advisor.adviseCall(request, callChain);
        advisor.adviseStream(request, streamChain);

        // 验证没有与 embedding model 或 cache manager 进行任何交互，直接放行
        verifyNoInteractions(embeddingModel);
        verifyNoInteractions(faqCacheManager);
    }

    @Test
    void shouldShortCircuitWhenFaqMatches() {
        CallAdvisorChain callChain = mock(CallAdvisorChain.class);
        AtomicBoolean nextCallTriggered = new AtomicBoolean(false);

        when(callChain.nextCall(any())).thenAnswer(inv -> {
            nextCallTriggered.set(true);
            return mock(ChatClientResponse.class);
        });

        // 设置意图为 FAQ
        IntentRecognitionResult intent = new IntentRecognitionResult(
                IntentType.FAQ, ConfidenceLevel.HIGH, Map.of(), List.of(), null, false, null
        );

        Map<String, Object> context = new HashMap<>();
        context.put("preRecognizedIntent", intent);
        ChatClientRequest request = new ChatClientRequest(new Prompt("发票怎么开"), context);

        // 模拟向量计算与缓存命中
        float[] vector = new float[]{0.1f, 0.2f};
        when(embeddingModel.embed("发票怎么开")).thenReturn(vector);
        when(faqCacheManager.match(vector)).thenReturn(
                new FaqCacheItem("faq-1", "发票怎么开？", "请在个人中心中开发票。", vector)
        );

        ChatClientResponse response = advisor.adviseCall(request, callChain);

        // 验证确实短路：没有触发 chain.nextCall，且返回了标准答案
        assertFalse(nextCallTriggered.get());
        assertNotNull(response);
        assertNotNull(response.chatResponse());
        assertNotNull(response.chatResponse().getResult());
        assertEquals("请在个人中心中开发票。", response.chatResponse().getResult().getOutput().getText());
    }

    @Test
    void shouldPassThroughWhenFaqDoesNotMatch() {
        CallAdvisorChain callChain = mock(CallAdvisorChain.class);
        AtomicBoolean nextCallTriggered = new AtomicBoolean(false);

        when(callChain.nextCall(any())).thenAnswer(inv -> {
            nextCallTriggered.set(true);
            return mock(ChatClientResponse.class);
        });

        IntentRecognitionResult intent = new IntentRecognitionResult(
                IntentType.FAQ, ConfidenceLevel.HIGH, Map.of(), List.of(), null, false, null
        );

        Map<String, Object> context = new HashMap<>();
        context.put("preRecognizedIntent", intent);
        ChatClientRequest request = new ChatClientRequest(new Prompt(new UserMessage("奇怪的提问")), context);

        float[] vector = new float[]{0.9f, 0.9f};
        when(embeddingModel.embed("奇怪的提问")).thenReturn(vector);
        when(faqCacheManager.match(vector)).thenReturn(null); // 缓存未命中

        advisor.adviseCall(request, callChain);

        // 验证：由于缓存未命中，应该正常放行到下一链条
        assertTrue(nextCallTriggered.get());
    }

    @Test
    void shouldShortCircuitStreamWhenFaqMatches() {
        StreamAdvisorChain streamChain = mock(StreamAdvisorChain.class);
        AtomicBoolean nextStreamTriggered = new AtomicBoolean(false);

        when(streamChain.nextStream(any())).thenAnswer(inv -> {
            nextStreamTriggered.set(true);
            return Flux.just(mock(ChatClientResponse.class));
        });

        IntentRecognitionResult intent = new IntentRecognitionResult(
                IntentType.FAQ, ConfidenceLevel.HIGH, Map.of(), List.of(), null, false, null
        );

        Map<String, Object> context = new HashMap<>();
        context.put("preRecognizedIntent", intent);
        ChatClientRequest request = new ChatClientRequest(new Prompt("配送费怎么收"), context);

        float[] vector = new float[]{0.3f, 0.4f};
        when(embeddingModel.embed("配送费怎么收")).thenReturn(vector);
        when(faqCacheManager.match(vector)).thenReturn(
                new FaqCacheItem("faq-2", "配送费规则", "首公里4元，超出后每公里加1元。", vector)
        );

        Flux<ChatClientResponse> stream = advisor.adviseStream(request, streamChain);
        assertNotNull(stream);

        List<ChatClientResponse> collected = stream.collectList().block();
        assertNotNull(collected);
        assertEquals(1, collected.size());
        
        ChatClientResponse response = collected.get(0);
        assertFalse(nextStreamTriggered.get());
        assertEquals("首公里4元，超出后每公里加1元。", response.chatResponse().getResult().getOutput().getText());
    }
}
