package com.weiqiang.skyai.rag.online.service;

import com.weiqiang.skyai.rag.online.config.OnlineRetrievalProperties;
import com.weiqiang.skyai.rag.online.model.RetrievedChunk;
import com.weiqiang.skyai.rag.online.model.SiliconFlowRerankRequest;
import com.weiqiang.skyai.rag.online.model.SiliconFlowRerankResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 SiliconFlow rerank API 的重排客户端。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SiliconFlowRerankerClient implements RerankerClient {

    private final RestClient.Builder restClientBuilder;
    private final OnlineRetrievalProperties properties;

    @Override
    public List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        SiliconFlowRerankResponse response = buildClient()
                .post()
                .uri("/v1/rerank")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new SiliconFlowRerankRequest(
                        properties.getSiliconFlow().getModel(),
                        query,
                        candidates.stream().map(RetrievedChunk::content).toList(),
                        candidates.size(),
                        true
                ))
                .retrieve()
                .body(SiliconFlowRerankResponse.class);

        if (response == null || response.results() == null || response.results().isEmpty()) {
            throw new IllegalStateException("SiliconFlow reranker response missing results");
        }

        Map<Integer, Double> rerankScores = parseScores(response.results(), candidates.size());
        List<RetrievedChunk> ranked = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            Double score = rerankScores.get(i);
            if (score == null) {
//                throw new IllegalStateException("SiliconFlow reranker response missing score for candidate " + i);
                log.warn("SiliconFlow 响应缺失索引 {} 的分数，将使用默认极低分", i);
                score = 0.000001;
                rerankScores.put(i, score); // 给一个保底分，防止 NullPointerException 或逻辑中断
            }
            ranked.add(candidates.get(i).withRerankScore(score));
        }

        ranked.sort(Comparator.comparingDouble((RetrievedChunk chunk) ->
                chunk.rerankScore() == null ? Double.NEGATIVE_INFINITY : chunk.rerankScore()).reversed());

        log.info("在线检索 Step2 完成，query={}，reranker模型={}，重排数={}",
                query, properties.getSiliconFlow().getModel(), ranked.size());
        return ranked;
    }

    private RestClient buildClient() {
        String apiKey = properties.getSiliconFlow().getApiKey();
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("SiliconFlow API key is required");
        }
        return restClientBuilder
                .baseUrl(properties.getSiliconFlow().getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }

    private Map<Integer, Double> parseScores(List<SiliconFlowRerankResponse.Result> results, int expectedCount) {
        Map<Integer, Double> scores = new LinkedHashMap<>();
        for (SiliconFlowRerankResponse.Result result : results) {
            if (result == null || result.index() < 0 || result.relevanceScore() == null) {
                continue;
            }
            scores.put(result.index(), result.relevanceScore());
        }
        if (scores.size() != expectedCount) {
            throw new IllegalStateException("SiliconFlow reranker response score count mismatch: expected "
                    + expectedCount + ", actual " + scores.size());
        }
        return scores;
    }
}
