package com.weiqiang.skyai.rag.online.service;

import com.weiqiang.skyai.rag.online.config.OnlineRetrievalProperties;
import com.weiqiang.skyai.rag.online.model.OllamaEmbedRequest;
import com.weiqiang.skyai.rag.online.model.OllamaEmbedResponse;
import com.weiqiang.skyai.rag.online.model.RetrievedChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 基于 Ollama embed API 的重排客户端
 *
 * @author weiqiang
 * @date 2024/6/17
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OllamaEmbedRerankerClient implements RerankerClient {

    private final RestClient.Builder restClientBuilder;
    private final OnlineRetrievalProperties properties;

    @Override
    public List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        List<String> inputs = new ArrayList<>(candidates.size() + 1);
        inputs.add(query);
        for (RetrievedChunk candidate : candidates) {
            inputs.add(candidate.content());
        }

        OllamaEmbedResponse response = buildClient()
                .post()
                .uri("/api/embed")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new OllamaEmbedRequest(properties.getOllama().getRerankerModel(), inputs, true))
                .retrieve()
                .body(OllamaEmbedResponse.class);

        if (response == null || response.embeddings() == null || response.embeddings().size() != inputs.size()) {
            throw new IllegalStateException("Ollama reranker response missing embeddings");
        }

        double[] queryVector = toPrimitiveArray(response.embeddings().get(0));
        List<RetrievedChunk> ranked = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            double score = cosineSimilarity(queryVector, toPrimitiveArray(response.embeddings().get(i + 1)));
            ranked.add(candidates.get(i).withRerankScore(score));
        }

        ranked.sort(Comparator.comparingDouble((RetrievedChunk chunk) -> chunk.rerankScore() == null ? Double.NEGATIVE_INFINITY : chunk.rerankScore())
                .reversed());

        log.info("在线检索 Step2 完成，query={}，reranker模型={}，重排数={}",
                query, properties.getOllama().getRerankerModel(), ranked.size());
        return ranked;
    }

    private RestClient buildClient() {
        return restClientBuilder
                .baseUrl(properties.getOllama().getBaseUrl())
                .build();
    }

    private static double[] toPrimitiveArray(List<Double> values) {
        double[] array = new double[values.size()];
        for (int i = 0; i < values.size(); i++) {
            Double value = values.get(i);
            array[i] = value == null ? 0.0d : value;
        }
        return array;
    }

    private static double cosineSimilarity(double[] left, double[] right) {
        int length = Math.min(left.length, right.length);
        double dot = 0.0d;
        double leftNorm = 0.0d;
        double rightNorm = 0.0d;
        for (int i = 0; i < length; i++) {
            double l = left[i];
            double r = right[i];
            dot += l * r;
            leftNorm += l * l;
            rightNorm += r * r;
        }
        if (leftNorm == 0.0d || rightNorm == 0.0d) {
            return 0.0d;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}
