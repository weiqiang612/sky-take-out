package com.weiqiang.skyai.rag.online.model;

import java.util.LinkedHashMap;
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
}
