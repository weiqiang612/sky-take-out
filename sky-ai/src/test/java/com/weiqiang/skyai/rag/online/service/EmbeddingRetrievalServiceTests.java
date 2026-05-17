package com.weiqiang.skyai.rag.online.service;

import com.weiqiang.skyai.rag.online.config.OnlineRetrievalProperties;
import com.weiqiang.skyai.rag.online.model.RetrievedChunk;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddingRetrievalServiceTests {

    @Test
    void retrieveCandidatesUsesVectorStoreSearchRequestAndMapsScores() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(OnlineRetrievalUnitTestConfiguration.class)) {
            OnlineRetrievalProperties properties = context.getBean(OnlineRetrievalProperties.class);
            properties.setTopK(55);
            properties.setSimilarityThreshold(0.15d);

            MutableRagIndexRepositoryStub repository = context.getBean(MutableRagIndexRepositoryStub.class);
            repository.setActiveDocumentIds(List.of("doc-1", "doc-2"));

            MutableVectorStoreStub vectorStoreStub = context.getBean(MutableVectorStoreStub.class);
            vectorStoreStub.setSearchResults(List.of(
                    Document.builder().text("first chunk").metadata(Map.of("documentId", "doc-1", "source", "doc-1")).score(0.91d).build(),
                    Document.builder().text("second chunk").metadata(Map.of("documentId", "doc-2", "source", "doc-2")).score(0.77d).build(),
                    Document.builder().text("inactive chunk").metadata(Map.of("documentId", "doc-3", "source", "doc-3")).score(0.66d).build()
            ));

            EmbeddingRetrievalService embeddingRetrievalService = context.getBean(EmbeddingRetrievalService.class);
            List<RetrievedChunk> candidates = embeddingRetrievalService.retrieveCandidates("hello world");

            assertEquals(2, candidates.size());
            assertEquals("first chunk", candidates.get(0).content());
            assertEquals(0.91d, candidates.get(0).embeddingScore());
            assertEquals("doc-1", candidates.get(0).metadata().get("source"));
            assertNull(candidates.get(0).rerankScore());
            SearchRequest request = vectorStoreStub.getLastSearchRequest();
            assertEquals("hello world", request.getQuery());
            assertEquals(55, request.getTopK());
            assertEquals(0.15d, request.getSimilarityThreshold());
            assertTrue(request.getFilterExpression() != null);
            assertEquals(55, properties.getTopK());
            assertEquals(0.15d, properties.getSimilarityThreshold());
        }
    }

    @Test
    void retrieveCandidatesReturnsEmptyWhenNoActiveDocuments() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(OnlineRetrievalUnitTestConfiguration.class)) {
            MutableRagIndexRepositoryStub repository = context.getBean(MutableRagIndexRepositoryStub.class);
            repository.setActiveDocumentIds(List.of());

            EmbeddingRetrievalService embeddingRetrievalService = context.getBean(EmbeddingRetrievalService.class);

            assertEquals(List.of(), embeddingRetrievalService.retrieveCandidates("hello world"));
        }
    }
}
