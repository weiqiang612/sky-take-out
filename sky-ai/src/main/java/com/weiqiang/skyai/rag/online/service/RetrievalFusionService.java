package com.weiqiang.skyai.rag.online.service;

import com.weiqiang.skyai.rag.online.config.OnlineRetrievalProperties;
import com.weiqiang.skyai.rag.online.model.RetrievedChunk;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RetrievalFusionService {

    private final OnlineRetrievalProperties properties;

    public List<RetrievedChunk> fuse(List<RetrievedChunk> embeddingCandidates, List<RetrievedChunk> keywordCandidates) {
        Map<String, Accumulator> merged = new LinkedHashMap<>();
        addRankedCandidates(merged, embeddingCandidates);
        addRankedCandidates(merged, keywordCandidates);

        int limit = Math.max(1, properties.getFusion().getMaxCandidates());
        return merged.values().stream()
                .map(Accumulator::toChunk)
                .sorted(Comparator
                        .comparingDouble((RetrievedChunk chunk) -> metadataDouble(chunk, "fusionScore")).reversed()
                        .thenComparing(Comparator.comparingDouble(RetrievedChunk::embeddingScore).reversed()))
                .limit(limit)
                .toList();
    }

    private void addRankedCandidates(Map<String, Accumulator> merged, List<RetrievedChunk> candidates) {
        for (int i = 0; i < candidates.size(); i++) {
            RetrievedChunk candidate = candidates.get(i);
            String key = uniqueKey(candidate);
            double contribution = 1.0d / (properties.getFusion().getRrfK() + i + 1.0d);
            merged.computeIfAbsent(key, ignored -> new Accumulator(candidate)).add(candidate, contribution);
        }
    }

    private String uniqueKey(RetrievedChunk chunk) {
        Object chunkHash = chunk.metadata().get("chunkHash");
        if (chunkHash != null && !chunkHash.toString().isBlank()) {
            return "chunkHash:" + chunkHash;
        }
        Object chunkId = chunk.metadata().get("chunkId");
        if (chunkId != null && !chunkId.toString().isBlank()) {
            return "chunkId:" + chunkId;
        }
        Object documentId = chunk.metadata().get("documentId");
        Object chunkIndex = chunk.metadata().get("chunkIndex");
        if (documentId != null && chunkIndex != null) {
            return "doc:" + documentId + "#" + chunkIndex;
        }
        return "content:" + chunk.content();
    }

    private static double metadataDouble(RetrievedChunk chunk, String key) {
        Object value = chunk.metadata().get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return 0.0d;
    }

    private static class Accumulator {

        private RetrievedChunk bestChunk;
        private final LinkedHashSet<String> sources = new LinkedHashSet<>();
        private String matchedQuery;
        private Double keywordScore;
        private double fusionScore;

        private Accumulator(RetrievedChunk bestChunk) {
            this.bestChunk = bestChunk;
        }

        private void add(RetrievedChunk chunk, double contribution) {
            if (chunk.embeddingScore() > bestChunk.embeddingScore()) {
                bestChunk = chunk;
            }
            fusionScore += contribution;
            collectSources(chunk);
            collectKeywordScore(chunk);
            collectMatchedQuery(chunk);
        }

        private RetrievedChunk toChunk() {
            return bestChunk.withRetrievalMetadata(new ArrayList<>(sources), keywordScore, fusionScore, matchedQuery);
        }

        private void collectSources(RetrievedChunk chunk) {
            Object value = chunk.metadata().get("retrievalSources");
            if (value instanceof Iterable<?> iterable) {
                for (Object item : iterable) {
                    if (item != null && !item.toString().isBlank()) {
                        sources.add(item.toString());
                    }
                }
                return;
            }
            if (value != null && !value.toString().isBlank()) {
                sources.add(value.toString());
            }
        }

        private void collectKeywordScore(RetrievedChunk chunk) {
            Object value = chunk.metadata().get("keywordScore");
            if (value instanceof Number number) {
                double score = number.doubleValue();
                keywordScore = keywordScore == null ? score : Math.max(keywordScore, score);
            }
        }

        private void collectMatchedQuery(RetrievedChunk chunk) {
            if (matchedQuery != null) {
                return;
            }
            Object value = chunk.metadata().get("matchedQuery");
            if (value != null && !value.toString().isBlank()) {
                matchedQuery = value.toString();
            }
        }
    }
}
