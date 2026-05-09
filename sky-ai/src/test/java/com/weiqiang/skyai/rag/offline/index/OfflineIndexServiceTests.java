package com.weiqiang.skyai.rag.offline.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiqiang.skyai.rag.offline.chunk.ChunkingStrategyRegistry;
import com.weiqiang.skyai.rag.offline.chunk.MarkdownChunkingStrategy;
import com.weiqiang.skyai.rag.offline.chunk.PdfChunkingStrategy;
import com.weiqiang.skyai.rag.offline.chunk.QaChunkingStrategy;
import com.weiqiang.skyai.rag.offline.chunk.TxtChunkingStrategy;
import com.weiqiang.skyai.rag.offline.model.EmbeddedRagChunk;
import com.weiqiang.skyai.rag.offline.model.OfflineIndexResponse;
import com.weiqiang.skyai.rag.offline.parse.DocumentParser;
import com.weiqiang.skyai.rag.offline.store.RagIndexRepository;
import com.weiqiang.skyai.rag.offline.model.RagDocumentSummary;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfflineIndexServiceTests {

    @Test
    void indexRunsParseChunkEmbedStoreAndVectorWrite() {
        FakeEmbeddingModel embeddingModel = new FakeEmbeddingModel();
        FakeVectorStore vectorStore = new FakeVectorStore();
        FakeRagIndexRepository repository = new FakeRagIndexRepository();
        OfflineIndexService service = newService(embeddingModel, vectorStore, repository);

        MockMultipartFile file = new MockMultipartFile(
                "file", "demo.txt", "text/plain", "hello offline rag".getBytes(StandardCharsets.UTF_8));

        OfflineIndexResponse response = service.index(file, "TXT");
        OfflineIndexResponse duplicateResponse = service.index(file, "TXT");

        assertEquals("demo.txt", response.sourceName());
        assertEquals("INDEXED", response.status());
        assertEquals(1, response.chunkCount());
        assertEquals(response.indexVersion(), repository.indexVersion);
        assertEquals("test-embedding", repository.embeddingModel);
        assertEquals("strategy=TXT", repository.chunkingSummary);
        assertEquals(response.documentId(), repository.documentId);
        assertEquals("demo.txt", repository.sourceName);
        assertEquals(1, repository.chunkCount);
        assertEquals(1, repository.chunks.size());
        assertEquals(response.documentId(), repository.chunks.get(0).documentId());
        assertEquals(1, vectorStore.documents.size());
        assertEquals(response.documentId(), vectorStore.documents.get(0).getMetadata().get("documentId"));
        assertTrue(vectorStore.documents.get(0).getMetadata().containsKey("chunkHash"));
        assertTrue(vectorStore.documents.get(0).getMetadata().containsKey("contentHash"));
        assertEquals(1, repository.saveDocumentIfAbsentCalls);
        assertEquals(response.documentId(), duplicateResponse.documentId());
        assertEquals(response.indexVersion(), duplicateResponse.indexVersion());
        assertEquals("DUPLICATE_SKIPPED", duplicateResponse.status());
        assertEquals(1, duplicateResponse.chunkCount());
        assertEquals(1, vectorStore.documents.size());
    }

    @Test
    void modifiedDocumentOnlyAddsChangedChunks() {
        FakeEmbeddingModel embeddingModel = new FakeEmbeddingModel();
        FakeVectorStore vectorStore = new FakeVectorStore();
        FakeRagIndexRepository repository = new FakeRagIndexRepository();
        OfflineIndexService service = newService(embeddingModel, vectorStore, repository);

        MockMultipartFile original = new MockMultipartFile(
                "file", "policy.md", "text/markdown",
                ("# Intro\nshared intro paragraph\n\n# Body\nshared stable paragraph").getBytes(StandardCharsets.UTF_8));
        MockMultipartFile modified = new MockMultipartFile(
                "file", "policy.md", "text/markdown",
                ("# Intro\nshared intro paragraph updated\n\n# Body\nshared stable paragraph").getBytes(StandardCharsets.UTF_8));

        OfflineIndexResponse first = service.index(original, "MARKDOWN");
        OfflineIndexResponse second = service.index(modified, "MARKDOWN");

        assertEquals("INDEXED", first.status());
        assertEquals(2, first.chunkCount());
        assertEquals("INDEXED", second.status());
        assertEquals(1, second.chunkCount());
        assertEquals(3, vectorStore.documents.size());
        assertEquals(3, repository.chunks.size());
        assertEquals(second.documentId(), repository.chunks.get(2).documentId());
        assertEquals(second.indexVersion(), vectorStore.documents.get(2).getMetadata().get("indexVersion"));
        assertEquals(second.documentId(), vectorStore.documents.get(2).getMetadata().get("documentId"));
        assertTrue(vectorStore.documents.get(2).getMetadata().containsKey("chunkHash"));
        assertTrue(vectorStore.documents.get(2).getMetadata().containsKey("contentHash"));
    }

    @Test
    void normalizedContentProducesStableHashAcrossLineEndingsAndTrailingWhitespace() {
        OfflineIndexService service = newService(new FakeEmbeddingModel(), new FakeVectorStore(), new FakeRagIndexRepository());

        String unixContent = "hello world\nsecond line";
        String windowsContent = "hello world\r\nsecond line   \r\n";

        assertEquals(service.sha256Hex(service.normalizeContent(unixContent)),
                service.sha256Hex(service.normalizeContent(windowsContent)));
    }

    @Test
    void offlineResponseDoesNotExposeJsonlPath() {
        List<String> components = Arrays.stream(OfflineIndexResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertFalse(components.contains("jsonlPath"));
    }

    @Test
    void serviceNoLongerProvidesLoadJsonlEntryPoint() {
        List<String> methods = Arrays.stream(OfflineIndexService.class.getDeclaredMethods())
                .map(Method::getName)
                .toList();

        assertFalse(methods.contains("loadJsonl"));
        assertTrue(methods.contains("index"));
    }

    private static class FakeEmbeddingModel implements EmbeddingModel {

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            return null;
        }

        @Override
        public float[] embed(Document document) {
            return new float[]{0.1f, 0.2f};
        }

        @Override
        public List<float[]> embed(List<String> texts) {
            return texts.stream().map(text -> new float[]{0.1f, 0.2f}).toList();
        }
    }

    private static class FakeVectorStore implements VectorStore {

        private final List<Document> documents = new ArrayList<>();

        @Override
        public void add(List<Document> documents) {
            this.documents.addAll(documents);
        }

        @Override
        public void delete(List<String> idList) {
        }

        @Override
        public void delete(Filter.Expression filterExpression) {
        }

        @Override
        public List<Document> similaritySearch(SearchRequest request) {
            return List.of();
        }
    }

    private static class FakeRagIndexRepository extends RagIndexRepository {

        private String indexVersion;
        private String embeddingModel;
        private String chunkingSummary;
        private String documentId;
        private String sourceName;
        private int chunkCount;
        private final List<EmbeddedRagChunk> chunks = new ArrayList<>();
        private int saveDocumentIfAbsentCalls;
        private final Map<String, RagDocumentSummary> documentsByHash = new HashMap<>();
        private final Map<String, String> documentHashById = new HashMap<>();
        private final java.util.Set<String> chunkHashes = new java.util.HashSet<>();

        FakeRagIndexRepository() {
            super(null, null, null, null);
        }

        @Override
        public void saveIndexVersion(String indexVersion, String embeddingModel, String chunkingSummary) {
            this.indexVersion = indexVersion;
            this.embeddingModel = embeddingModel;
            this.chunkingSummary = chunkingSummary;
        }

        @Override
        public boolean saveDocumentIfAbsent(String documentId, String sourceName,
                                            com.weiqiang.skyai.rag.offline.model.DocumentType documentType,
                                            String indexVersion, String contentHash, int chunkCount, String status) {
            saveDocumentIfAbsentCalls++;
            if (documentsByHash.containsKey(contentHash)) {
                return false;
            }
            this.documentId = documentId;
            this.sourceName = sourceName;
            this.chunkCount = chunkCount;
            this.documentHashById.put(documentId, contentHash);
            this.documentsByHash.put(contentHash, new RagDocumentSummary(
                    documentId,
                    sourceName,
                    documentType,
                    indexVersion,
                    status,
                    chunkCount,
                    OffsetDateTime.now(),
                    OffsetDateTime.now()
            ));
            return true;
        }

        @Override
        public Optional<RagDocumentSummary> findDocumentByContentHash(String contentHash) {
            return Optional.ofNullable(documentsByHash.get(contentHash));
        }

        @Override
        public List<String> findChunkHashes() {
            return new ArrayList<>(chunkHashes);
        }

        @Override
        public void updateDocumentStatus(String documentId, int chunkCount, String status) {
            String contentHash = documentHashById.get(documentId);
            if (contentHash == null) {
                return;
            }
            RagDocumentSummary existing = documentsByHash.get(contentHash);
            if (existing == null) {
                return;
            }
            RagDocumentSummary updated = new RagDocumentSummary(
                    existing.documentId(),
                    existing.sourceName(),
                    existing.documentType(),
                    existing.indexVersion(),
                    status,
                    chunkCount,
                    existing.createdAt(),
                    OffsetDateTime.now()
            );
            documentsByHash.put(contentHash, updated);
            this.chunkCount = chunkCount;
        }

        @Override
        public List<RagDocumentSummary> findDocuments() {
            return new ArrayList<>(documentsByHash.values());
        }

        @Override
        public void saveChunks(List<EmbeddedRagChunk> chunks) {
            this.chunks.addAll(chunks);
            for (EmbeddedRagChunk chunk : chunks) {
                this.chunkHashes.add(chunk.chunkHash());
            }
        }
    }

    private OfflineIndexService newService(FakeEmbeddingModel embeddingModel, FakeVectorStore vectorStore,
                                           FakeRagIndexRepository repository) {
        OfflineIndexService service = new OfflineIndexService(
                new DocumentParser(),
                new ChunkingStrategyRegistry(List.of(
                        new QaChunkingStrategy(new ObjectMapper()),
                        new MarkdownChunkingStrategy(),
                        new PdfChunkingStrategy(),
                        new TxtChunkingStrategy()
                )),
                embeddingModel,
                vectorStore,
                repository
        );
        ReflectionTestUtils.setField(service, "embeddingModelName", "test-embedding");
        return service;
    }
}
