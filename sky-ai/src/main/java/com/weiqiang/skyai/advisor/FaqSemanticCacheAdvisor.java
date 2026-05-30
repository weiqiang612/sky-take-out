package com.weiqiang.skyai.advisor;

import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionResult;
import com.weiqiang.skyai.intent_recognition.model.IntentType;
import com.weiqiang.skyai.rag.online.manager.FaqCacheManager;
import com.weiqiang.skyai.rag.online.model.FaqCacheItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * FAQ 语义缓存前置 Advisor，优先在 RAG 检索和大模型推理之前进行语义命中短路拦截。
 *
 * @author antigravity
 * @date 2026/05/30
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FaqSemanticCacheAdvisor implements CallAdvisor, StreamAdvisor {

    private final FaqCacheManager faqCacheManager;
    private final EmbeddingModel embeddingModel;

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        if (!shouldIntercept(chatClientRequest)) {
            return callAdvisorChain.nextCall(chatClientRequest);
        }

        String userText = extractUserText(chatClientRequest);
        if (StringUtils.hasText(userText)) {
            FaqCacheItem matched = tryMatch(userText);
            if (matched != null) {
                log.info("FaqSemanticCacheAdvisor adviseCall Short-circuit triggered for question: {}", userText);
                return createShortCircuitResponse(chatClientRequest, matched.getAnswer());
            }
        }

        return callAdvisorChain.nextCall(chatClientRequest);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        if (!shouldIntercept(chatClientRequest)) {
            return streamAdvisorChain.nextStream(chatClientRequest);
        }

        String userText = extractUserText(chatClientRequest);
        if (StringUtils.hasText(userText)) {
            FaqCacheItem matched = tryMatch(userText);
            if (matched != null) {
                log.info("FaqSemanticCacheAdvisor adviseStream Short-circuit triggered for question: {}", userText);
                ChatClientResponse shortResponse = createShortCircuitResponse(chatClientRequest, matched.getAnswer());
                return Flux.just(shortResponse);
            }
        }

        return streamAdvisorChain.nextStream(chatClientRequest);
    }

    @Override
    public String getName() {
        return "faqSemanticCacheAdvisor";
    }

    @Override
    public int getOrder() {
        // 执行顺序在 IntentRecognitionAdvisor (HIGHEST) 之后，但是在 RagAdvisor (HIGHEST + 3) 之前
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    /**
     * 判断是否属于需要拦截并尝试语义匹配的 FAQ 意图
     */
    private boolean shouldIntercept(ChatClientRequest request) {
        if (request == null || request.context() == null) {
            return false;
        }
        Object preIntentObj = request.context().get("preRecognizedIntent");
        if (preIntentObj instanceof IntentRecognitionResult) {
            IntentRecognitionResult preIntent = (IntentRecognitionResult) preIntentObj;
            return preIntent.intent() == IntentType.FAQ;
        }
        return false;
    }

    /**
     * 对提问文本进行向量化并匹配本地 FAQ 缓存
     */
    private FaqCacheItem tryMatch(String userText) {
        try {
            float[] queryVector = embeddingModel.embed(userText);
            return faqCacheManager.match(queryVector);
        } catch (Exception ex) {
            log.error("Failed to generate query vector or execute similarity match", ex);
            return null;
        }
    }

    /**
     * 手动封装被拦截短路的 ChatClientResponse
     */
    private ChatClientResponse createShortCircuitResponse(ChatClientRequest request, String answerText) {
        ChatResponse chatResponse = ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage(answerText))))
                .build();
        return ChatClientResponse.builder()
                .chatResponse(chatResponse)
                .context(request.context())
                .build();
    }

    /**
     * 提取提问正文
     */
    private String extractUserText(ChatClientRequest chatClientRequest) {
        if (chatClientRequest == null || chatClientRequest.prompt() == null 
                || chatClientRequest.prompt().getUserMessage() == null) {
            return null;
        }
        String text = chatClientRequest.prompt().getUserMessage().getText();
        return StringUtils.hasText(text) ? text : null;
    }
}
