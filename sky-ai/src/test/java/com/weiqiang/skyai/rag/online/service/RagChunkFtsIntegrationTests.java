package com.weiqiang.skyai.rag.online.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiqiang.skyai.rag.offline.store.RagChunkEntity;
import com.weiqiang.skyai.rag.offline.store.RagIndexRepository;
import com.weiqiang.skyai.rag.offline.store.RagChunkSearchRepository;
import com.weiqiang.skyai.rag.offline.store.TsQueryFormatter;
import com.weiqiang.skyai.rag.online.config.OnlineRetrievalProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Disabled;
import org.opentest4j.TestAbortedException;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(initializers = RagChunkFtsIntegrationTests.PostgresInitializer.class)
@Disabled("Requires Docker/Testcontainers PostgreSQL")
@Import({
        RagIndexRepository.class,
        TsQueryFormatter.class,
        QueryExpansionService.class,
        EmbeddingRetrievalService.class,
        KeywordRetrievalService.class,
        RetrievalFusionService.class,
        CandidateRetrievalService.class,
        RagChunkFtsIntegrationTests.TestBeans.class
})
class RagChunkFtsIntegrationTests {

    static final PostgreSQLContainer<?> POSTGRESQL = new PostgreSQLContainer<>("postgres:15.8")
            .withDatabaseName("sky_ai")
            .withUsername("postgres")
            .withPassword("postgres");

    @AfterAll
    static void stopContainer() {
        if (POSTGRESQL.isRunning()) {
            POSTGRESQL.stop();
        }
    }

    @Test
    void keywordSearchUsesStemmingAndReturnsRankedResults(JdbcTemplate jdbcTemplate, KeywordRetrievalService keywordRetrievalService) {
        insertChunk(jdbcTemplate, "doc-1", "chunk-1", "hash-1",
                "Learning guide", "The model learned from examples.", 0);
        insertChunk(jdbcTemplate, "doc-2", "chunk-2", "hash-2",
                "Learning notes", "Learning learning learning is repeated here.", 1);

        List<com.weiqiang.skyai.rag.online.model.RetrievedChunk> candidates =
                keywordRetrievalService.retrieveCandidates("learning");

        assertEquals(2, candidates.size());
        assertEquals("Learning notes", candidates.get(0).metadata().get("title"));
        assertTrue(asDouble(candidates.get(0).metadata().get("keywordScore")) > 0.0d);
        assertTrue(asDouble(candidates.get(0).metadata().get("keywordScore"))
                >= asDouble(candidates.get(1).metadata().get("keywordScore")));
        assertEquals("learning", candidates.get(0).metadata().get("matchedQuery"));
    }

    @Test
    void keywordSearchSkipsInactiveDocuments(JdbcTemplate jdbcTemplate, KeywordRetrievalService keywordRetrievalService) {
        insertChunk(jdbcTemplate, "doc-1", "chunk-1", "hash-1",
                "Learning guide", "The model learned from examples.", 0);
        insertChunk(jdbcTemplate, "doc-2", "chunk-2", "hash-2",
                "Learning notes", "Learning learning learning is repeated here.", 1);
        jdbcTemplate.update("update rag_document set active = false where document_id = ?", "doc-2");

        List<com.weiqiang.skyai.rag.online.model.RetrievedChunk> candidates =
                keywordRetrievalService.retrieveCandidates("learning");

        assertEquals(1, candidates.size());
        assertEquals("Learning guide", candidates.get(0).metadata().get("title"));
    }

    @Test
    void hybridMergeKeepsFtsAndVectorCandidatesOrdered(JdbcTemplate jdbcTemplate,
                                                      MutableVectorStoreStub vectorStore,
                                                      CandidateRetrievalService candidateRetrievalService) {
        insertChunk(jdbcTemplate, "doc-10", "chunk-shared", "hash-shared",
                "Hybrid shared", "The model learned from examples.", 0);
        insertChunk(jdbcTemplate, "doc-11", "chunk-keyword", "hash-keyword",
                "Hybrid keyword", "Keyword only evidence.", 1);

        vectorStore.setSearchResults("learning", List.of(
                Document.builder().text("vector shared")
                        .metadata(Map.of("chunkHash", "hash-shared", "title", "Hybrid shared"))
                        .score(0.93d)
                        .build(),
                Document.builder().text("vector only")
                        .metadata(Map.of("chunkHash", "hash-vector", "title", "Vector only"))
                        .score(0.81d)
                        .build()
        ));

        List<com.weiqiang.skyai.rag.online.model.RetrievedChunk> fused =
                candidateRetrievalService.retrieveCandidates("learning");

        assertEquals(3, fused.size());
        assertEquals("vector shared", fused.get(0).content());
        assertEquals("vector only", fused.get(1).content());
        assertEquals("Keyword only evidence.", fused.get(2).content());
    }

    private void insertChunk(JdbcTemplate jdbcTemplate, String documentId, String chunkId, String chunkHash,
                             String title, String content, int chunkIndex) {
        jdbcTemplate.update("""
                        insert into rag_document(document_id, source_name, document_type, index_version, content_hash, status, active, chunk_count)
                        values (?, ?, ?, ?, ?, ?, true, ?)
                        on conflict (document_id) do nothing
                        """,
                documentId, title, "TXT", "v-test", "content-" + documentId, "INDEXED", 1);

        jdbcTemplate.update("""
                        insert into rag_chunk(chunk_id, chunk_hash, document_id, index_version, chunk_index, content, metadata_json, vector_store_id)
                        values (?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                        on conflict (chunk_id) do nothing
                        """,
                chunkId, chunkHash, documentId, "v-test", chunkIndex, content,
                json(Map.of(
                        "title", title,
                        "chunkHash", chunkHash,
                        "documentId", documentId,
                        "chunkIndex", chunkIndex
                )),
                chunkId);
    }

    private String json(Map<String, Object> metadata) {
        try {
            return new ObjectMapper().writeValueAsString(metadata);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return 0.0d;
    }

    @TestConfiguration
    @EnableJpaRepositories(basePackageClasses = RagChunkSearchRepository.class)
    @EntityScan(basePackageClasses = RagChunkEntity.class)
    static class TestBeans {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        OnlineRetrievalProperties onlineRetrievalProperties() {
            return new OnlineRetrievalProperties();
        }

        @Bean
        MutableVectorStoreStub vectorStore() {
            return new MutableVectorStoreStub();
        }
    }

    static class PostgresInitializer implements org.springframework.context.ApplicationContextInitializer<org.springframework.context.ConfigurableApplicationContext> {

        @Override
        public void initialize(org.springframework.context.ConfigurableApplicationContext context) {
            if (!DockerClientFactory.instance().isDockerAvailable()) {
                throw new TestAbortedException("Docker is not available for Testcontainers PostgreSQL integration tests");
            }
            if (!POSTGRESQL.isRunning()) {
                POSTGRESQL.start();
            }
            TestPropertyValues.of(
                    "spring.datasource.url=" + POSTGRESQL.getJdbcUrl(),
                    "spring.datasource.username=" + POSTGRESQL.getUsername(),
                    "spring.datasource.password=" + POSTGRESQL.getPassword(),
                    "spring.datasource.driver-class-name=" + POSTGRESQL.getDriverClassName()
            ).applyTo(context.getEnvironment());
        }
    }
}
