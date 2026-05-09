package com.weiqiang.skyai.rag.online.service;

import com.weiqiang.skyai.rag.online.config.OnlineRetrievalProperties;
import com.weiqiang.skyai.rag.online.model.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetrievalFusionServiceTests {

    @Test
    void fuseDeduplicatesByChunkHashAndPreservesSources() {
        OnlineRetrievalProperties properties = new OnlineRetrievalProperties();
        properties.getFusion().setMaxCandidates(10);
        properties.getFusion().setRrfK(60);
        RetrievalFusionService service = new RetrievalFusionService(properties);

        RetrievedChunk embedding = new RetrievedChunk(
                "same chunk",
                Map.of("chunkHash", "hash-1", "retrievalSources", List.of("embedding_original"), "matchedQuery", "original"),
                0.8d,
                null
        );
        RetrievedChunk keyword = new RetrievedChunk(
                "same chunk",
                Map.of("chunkHash", "hash-1", "retrievalSources", List.of("keyword"), "keywordScore", 3.0d, "matchedQuery", "original"),
                0.0d,
                null
        );

        List<RetrievedChunk> fused = service.fuse(List.of(embedding), List.of(keyword));

        assertEquals(1, fused.size());
        assertEquals(List.of("embedding_original", "keyword"), fused.get(0).metadata().get("retrievalSources"));
        assertEquals(3.0d, fused.get(0).metadata().get("keywordScore"));
        assertTrue((Double) fused.get(0).metadata().get("fusionScore") > 0.0d);
    }

    @Test
    void fuseLimitsCandidatesByConfiguredMaximum() {
        OnlineRetrievalProperties properties = new OnlineRetrievalProperties();
        properties.getFusion().setMaxCandidates(1);
        RetrievalFusionService service = new RetrievalFusionService(properties);

        List<RetrievedChunk> fused = service.fuse(List.of(
                new RetrievedChunk("a", Map.of("chunkHash", "a", "retrievalSources", List.of("embedding_original")), 0.1d, null),
                new RetrievedChunk("b", Map.of("chunkHash", "b", "retrievalSources", List.of("embedding_original")), 0.9d, null)
        ), List.of());

        assertEquals(1, fused.size());
    }
}
