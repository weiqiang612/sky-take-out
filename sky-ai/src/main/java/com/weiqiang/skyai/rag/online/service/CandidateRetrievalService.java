package com.weiqiang.skyai.rag.online.service;

import com.weiqiang.skyai.rag.online.model.RetrievedChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CandidateRetrievalService {

    private final QueryExpansionService queryExpansionService;
    private final EmbeddingRetrievalService embeddingRetrievalService;
    private final KeywordRetrievalService keywordRetrievalService;
    private final RetrievalFusionService retrievalFusionService;

    public List<RetrievedChunk> retrieveCandidates(String query) {
        LinkedHashSet<String> searchQueries = new LinkedHashSet<>();
        searchQueries.add(query);
        searchQueries.addAll(queryExpansionService.expand(query));
        log.info("查询扩展后的问题为：{}", searchQueries);
        List<RetrievedChunk> embeddingCandidates = embeddingRetrievalService.retrieveCandidates(new ArrayList<>(searchQueries));
        List<RetrievedChunk> keywordCandidates = keywordRetrievalService.retrieveCandidates(query);
        List<RetrievedChunk> fused = retrievalFusionService.fuse(embeddingCandidates, keywordCandidates);

        log.info("在线检索候选生成完成，query={}，扩展query数={}，向量候选数={}，关键词候选数={}，融合候选数={}",
                query, Math.max(0, searchQueries.size() - 1), embeddingCandidates.size(), keywordCandidates.size(), fused.size());
        return fused;
    }
}
