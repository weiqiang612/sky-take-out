package com.weiqiang.skyai.rag.online.service;

import com.weiqiang.skyai.rag.online.config.OnlineRetrievalProperties;
import com.weiqiang.skyai.rag.online.model.RetrievalResult;
import com.weiqiang.skyai.rag.online.model.RetrievedChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 上下文组装服务
 *
 * @author weiqiang
 * @date 2024/6/17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContextAssemblyService {

    private final OnlineRetrievalProperties properties;

    public RetrievalResult assemble(String query, List<RetrievedChunk> chunks) {
        String context = chunks.stream()
                .map(this::resolveChunkText)
                .reduce((left, right) -> left + properties.getContext().getChunkSeparator() + right)
                .orElse("");

        log.info("在线检索 Step3 完成，query={}，最终上下文长度={}", query, context.length());
        return new RetrievalResult(query, context, chunks);
    }

    private String resolveChunkText(RetrievedChunk chunk) {
        for (String key : properties.getContext().getPreferredTextMetadataKeys()) {
            Object value = chunk.metadata().get(key);
            if (value instanceof String text && !text.isBlank()) {
                return text;
            }
        }
        return chunk.content();
    }
}
