package com.weiqiang.skyai.rag.online.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record RetrievedChunk(
        String content,
        Map<String, Object> metadata,
        double embeddingScore,
        Double rerankScore
) {

    public RetrievedChunk {
        metadata = metadata == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(metadata));
    }

    public RetrievedChunk withRerankScore(Double score) {
        return new RetrievedChunk(content, metadata, embeddingScore, score);
    }

    public RetrievedChunk withMetadata(Map<String, Object> updatedMetadata) {
        return new RetrievedChunk(content, updatedMetadata, embeddingScore, rerankScore);
    }

    public RetrievedChunk withRetrievalMetadata(List<String> retrievalSources, Double keywordScore,
                                                double fusionScore, String matchedQuery) {
        Map<String, Object> updated = new LinkedHashMap<>(metadata);
        updated.put("retrievalSources", retrievalSources == null ? List.of() : List.copyOf(retrievalSources));
        if (keywordScore != null) {
            updated.put("keywordScore", keywordScore);
        }
        updated.put("fusionScore", fusionScore);
        if (matchedQuery != null && !matchedQuery.isBlank()) {
            updated.put("matchedQuery", matchedQuery);
        }
        return withMetadata(updated);
    }
}
