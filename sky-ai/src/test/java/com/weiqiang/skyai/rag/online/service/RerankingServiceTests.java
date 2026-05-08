package com.weiqiang.skyai.rag.online.service;

import com.weiqiang.skyai.rag.online.config.OnlineRetrievalProperties;
import com.weiqiang.skyai.rag.online.model.RetrievedChunk;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RerankingServiceTests {

    @Test
    void rerankUsesRerankerOutputAndTrimsToTopN() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(OnlineRetrievalUnitTestConfiguration.class)) {
            OnlineRetrievalProperties properties = context.getBean(OnlineRetrievalProperties.class);
            properties.setTopN(2);

            List<RetrievedChunk> candidates = List.of(
                    new RetrievedChunk("a", Map.of("source", "1"), 0.10d, null),
                    new RetrievedChunk("b", Map.of("source", "2"), 0.20d, null),
                    new RetrievedChunk("c", Map.of("source", "3"), 0.30d, null)
            );

            MutableRerankerClientStub rerankerClientStub = context.getBean(MutableRerankerClientStub.class);
            rerankerClientStub.setRerankedResults(List.of(
                    candidates.get(2).withRerankScore(0.99d),
                    candidates.get(1).withRerankScore(0.88d),
                    candidates.get(0).withRerankScore(0.11d)
            ));

            RerankingService rerankingService = context.getBean(RerankingService.class);
            List<RetrievedChunk> reranked = rerankingService.rerank("query", candidates);

            assertEquals(2, reranked.size());
            assertEquals("c", reranked.get(0).content());
            assertEquals(0.99d, reranked.get(0).rerankScore());
            assertEquals("b", reranked.get(1).content());
        }
    }

    @Test
    void rerankFallsBackToEmbeddingResultsWhenClientFails() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(OnlineRetrievalUnitTestConfiguration.class)) {
            OnlineRetrievalProperties properties = context.getBean(OnlineRetrievalProperties.class);
            properties.setTopN(2);
            properties.setFallbackToEmbeddingResultsOnRerankFailure(true);

            List<RetrievedChunk> candidates = List.of(
                    new RetrievedChunk("a", Map.of("source", "1"), 0.10d, null),
                    new RetrievedChunk("b", Map.of("source", "2"), 0.30d, null),
                    new RetrievedChunk("c", Map.of("source", "3"), 0.20d, null)
            );

            MutableRerankerClientStub rerankerClientStub = context.getBean(MutableRerankerClientStub.class);
            rerankerClientStub.setFailure(new IllegalStateException("rerank failed"));

            RerankingService rerankingService = context.getBean(RerankingService.class);
            List<RetrievedChunk> reranked = rerankingService.rerank("query", candidates);

            assertEquals(2, reranked.size());
            assertEquals("b", reranked.get(0).content());
            assertEquals("c", reranked.get(1).content());
        }
    }
}
