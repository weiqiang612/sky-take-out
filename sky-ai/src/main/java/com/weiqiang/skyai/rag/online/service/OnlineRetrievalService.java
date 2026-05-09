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

    private final CandidateRetrievalService candidateRetrievalService;
    private final RerankingService rerankingService;
    private final ContextAssemblyService contextAssemblyService;

    public RetrievalResult retrieve(String query) {
        log.info("在线检索开始，query={}", query);
        // 1. 候选生成：通过查询扩展、向量检索和关键词检索等方式生成候选文本块
        List<RetrievedChunk> candidates = candidateRetrievalService.retrieveCandidates(query);
        // 2. 重排：对候选文本块进行重排，以提升相关性
        List<RetrievedChunk> finalChunks = rerankingService.rerank(query, candidates);
        // 3. 上下文组装：将重排后的文本块组装成最终的检索结果
        return contextAssemblyService.assemble(query, finalChunks);
    }
}
