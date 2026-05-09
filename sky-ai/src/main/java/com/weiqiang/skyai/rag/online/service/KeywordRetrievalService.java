package com.weiqiang.skyai.rag.online.service;

import com.weiqiang.skyai.rag.offline.model.KeywordSearchResult;
import com.weiqiang.skyai.rag.offline.store.KeywordChunkRepository;
import com.weiqiang.skyai.rag.online.config.OnlineRetrievalProperties;
import com.weiqiang.skyai.rag.online.model.RetrievedChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeywordRetrievalService {

    private final ObjectProvider<KeywordChunkRepository> repositoryProvider;
    private final OnlineRetrievalProperties properties;

    public List<RetrievedChunk> retrieveCandidates(String query) {
        OnlineRetrievalProperties.Keyword config = properties.getKeyword();
        if (!config.isEnabled() || config.getTopK() <= 0 || !StringUtils.hasText(query)) {
            return List.of();
        }

        KeywordChunkRepository repository = repositoryProvider.getIfAvailable();
        if (repository == null) {
            log.debug("关键词检索跳过：RagIndexRepository 不可用");
            return List.of();
        }

        List<KeywordSearchResult> results = repository.searchChunksByKeyword(query, config.getTopK());
        List<RetrievedChunk> candidates = new ArrayList<>(results.size());
        for (KeywordSearchResult result : results) {
            Map<String, Object> metadata = new LinkedHashMap<>(result.metadata());
            metadata.put("retrievalSources", List.of("keyword"));
            metadata.put("keywordScore", result.score());
            metadata.put("matchedQuery", query != null ? query : "");

            metadata.values().removeIf(Objects::isNull);

            candidates.add(new RetrievedChunk(
                    result.content() == null ? "" : result.content(),
                    metadata,
                    0.0d,
                    null
            ));
        }
        log.info("在线检索关键词召回完成，query={}，候选数={}", query, candidates.size());
        return candidates;
    }
}
