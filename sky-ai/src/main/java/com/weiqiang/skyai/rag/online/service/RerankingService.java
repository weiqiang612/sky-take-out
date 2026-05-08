package com.weiqiang.skyai.rag.online.service;

import com.weiqiang.skyai.rag.online.config.OnlineRetrievalProperties;
import com.weiqiang.skyai.rag.online.model.RetrievedChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * 重排服务，负责对向量检索得到的候选文本进行重排，以提升最终结果的相关性
 *
 * @author weiqiang
 * @date 2024/6/17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RerankingService {

    private final RerankerClient rerankerClient;
    private final OnlineRetrievalProperties properties;

    public List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        try {
            List<RetrievedChunk> reranked = rerankerClient.rerank(query, candidates);
            int limit = Math.min(properties.getTopN(), reranked.size());
            List<RetrievedChunk> top = reranked.subList(0, limit);
            log.info("在线检索 Step2 输出 topN={}，query={}", top.size(), query);
            log.debug("reranker scores={}", top.stream().map(chunk -> chunk.rerankScore()).toList());
            return top;
        } catch (Exception ex) {
            log.error("Reranker 调用失败，query={}，是否回退到 embedding 结果={}", query,
                    properties.isFallbackToEmbeddingResultsOnRerankFailure(), ex);
            if (properties.isFallbackToEmbeddingResultsOnRerankFailure()) {
                int limit = Math.min(properties.getTopN(), candidates.size());
                return candidates.stream()
                        .sorted(Comparator.comparingDouble(RetrievedChunk::embeddingScore).reversed())
                        .limit(limit)
                        .toList();
            }
            throw ex;
        }
    }
}
