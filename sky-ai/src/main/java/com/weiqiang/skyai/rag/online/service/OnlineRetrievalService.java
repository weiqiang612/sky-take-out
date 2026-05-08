package com.weiqiang.skyai.rag.online.service;

import com.weiqiang.skyai.rag.online.model.RetrievalResult;
import com.weiqiang.skyai.rag.online.model.RetrievedChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 在线检索服务，负责协调向量检索、重排和上下文组装三个步骤
 *
 * @author weiqiang
 * @date 2024/6/17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnlineRetrievalService {

    private final EmbeddingRetrievalService embeddingRetrievalService;
    private final RerankingService rerankingService;
    private final ContextAssemblyService contextAssemblyService;

    public RetrievalResult retrieve(String query) {
        log.info("在线检索开始，query={}", query);
        List<RetrievedChunk> candidates = embeddingRetrievalService.retrieveCandidates(query);
        List<RetrievedChunk> finalChunks = rerankingService.rerank(query, candidates);
        return contextAssemblyService.assemble(query, finalChunks);
    }
}
