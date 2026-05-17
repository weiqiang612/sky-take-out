package com.weiqiang.skyai.rag.online.service;

import com.weiqiang.skyai.rag.offline.model.KeywordSearchResult;
import com.weiqiang.skyai.rag.online.config.OnlineRetrievalProperties;
import com.weiqiang.skyai.rag.online.model.RetrievedChunk;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KeywordRetrievalServiceTests {

    @Test
    void retrieveCandidatesMapsKeywordResultsAndUsesConfiguredTopK() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(KeywordRetrievalServiceTestConfiguration.class)) {
            OnlineRetrievalProperties properties = context.getBean(OnlineRetrievalProperties.class);
            properties.getKeyword().setTopK(1);

            MutableKeywordChunkRepositoryStub repository = context.getBean(MutableKeywordChunkRepositoryStub.class);
            repository.setActiveDocumentIds(List.of("doc-1", "doc-2"));
            repository.setResults(List.of(
                    new KeywordSearchResult("包含 callback 关键词", Map.of("chunkHash", "hash-1", "documentId", "doc-1"), 2.3d),
                    new KeywordSearchResult("另一个 callback 文本", Map.of("chunkHash", "hash-2", "documentId", "doc-2"), 1.1d)
            ));

            KeywordRetrievalService service = context.getBean(KeywordRetrievalService.class);
            List<RetrievedChunk> candidates = service.retrieveCandidates("callback");

            assertEquals(1, candidates.size());
            assertEquals("callback", repository.getLastQuery());
            assertEquals(1, repository.getLastTopK());
            assertEquals("包含 callback 关键词", candidates.get(0).content());
            assertEquals(List.of("keyword"), candidates.get(0).metadata().get("retrievalSources"));
            assertEquals(2.3d, candidates.get(0).metadata().get("keywordScore"));
            assertEquals("callback", candidates.get(0).metadata().get("matchedQuery"));
        }
    }

    @Test
    void retrieveCandidatesFiltersInactiveDocumentsByDocumentId() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(KeywordRetrievalServiceTestConfiguration.class)) {
            MutableKeywordChunkRepositoryStub repository = context.getBean(MutableKeywordChunkRepositoryStub.class);
            repository.setActiveDocumentIds(List.of("doc-1"));
            repository.setResults(List.of(
                    new KeywordSearchResult("active keyword text", Map.of("chunkHash", "hash-1", "documentId", "doc-1"), 2.3d),
                    new KeywordSearchResult("inactive keyword text", Map.of("chunkHash", "hash-2", "documentId", "doc-2"), 1.1d)
            ));

            KeywordRetrievalService service = context.getBean(KeywordRetrievalService.class);
            List<RetrievedChunk> candidates = service.retrieveCandidates("callback");

            assertEquals(1, candidates.size());
            assertEquals("active keyword text", candidates.get(0).content());
        }
    }

    @Test
    void retrieveCandidatesReturnsEmptyWhenDisabled() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(KeywordRetrievalServiceTestConfiguration.class)) {
            OnlineRetrievalProperties properties = context.getBean(OnlineRetrievalProperties.class);
            properties.getKeyword().setEnabled(false);

            KeywordRetrievalService service = context.getBean(KeywordRetrievalService.class);

            assertEquals(List.of(), service.retrieveCandidates("中文关键词"));
        }
    }
}
