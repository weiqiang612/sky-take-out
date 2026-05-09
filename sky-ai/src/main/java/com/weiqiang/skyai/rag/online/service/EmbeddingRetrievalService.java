package com.weiqiang.skyai.rag.online.service;

import com.weiqiang.skyai.rag.online.config.OnlineRetrievalProperties;
import com.weiqiang.skyai.rag.online.model.RetrievedChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于向量检索的候选文本检索服务
 *
 * @author weiqiang
 * @date 2024/6/17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingRetrievalService {

    private final VectorStore vectorStore;
    private final OnlineRetrievalProperties properties;

    public List<RetrievedChunk> retrieveCandidates(String query) {
        return retrieveCandidates(query, "embedding_original");
    }

    public List<RetrievedChunk> retrieveCandidates(List<String> queries) {
        List<RetrievedChunk> candidates = new ArrayList<>();
        for (int i = 0; i < queries.size(); i++) {
            String source = i == 0 ? "embedding_original" : "embedding_expanded";
            candidates.addAll(retrieveCandidates(queries.get(i), source));
        }
        return candidates;
    }

    private List<RetrievedChunk> retrieveCandidates(String query, String retrievalSource) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(properties.getTopK())
                .similarityThreshold(properties.getSimilarityThreshold())
                .build();

        List<Document> documents = vectorStore.similaritySearch(request);
        List<RetrievedChunk> candidates = new ArrayList<>(documents.size());
        for (Document document : documents) {
            candidates.add(toRetrievedChunk(document, retrievalSource, query));
        }

        log.info("在线检索向量召回完成，source={}，query={}，候选数={}", retrievalSource, query, candidates.size());
        return candidates;
    }

    private RetrievedChunk toRetrievedChunk(Document document, String retrievalSource, String matchedQuery) {
        Double score = document.getScore();
        Map<String, Object> metadata = new LinkedHashMap<>(document.getMetadata());
        metadata.put("retrievalSources", List.of(retrievalSource));
        metadata.put("matchedQuery", matchedQuery);
        return new RetrievedChunk(
                document.getText() == null ? "" : document.getText(),
                metadata,
                score == null ? 0.0d : score,
                null
        );
    }
}
