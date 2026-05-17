package com.weiqiang.skyai.rag.offline.service;

import com.weiqiang.skyai.rag.offline.model.DocumentType;
import com.weiqiang.skyai.rag.offline.model.RagDocumentSummary;
import com.weiqiang.skyai.rag.offline.store.RagIndexRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OfflineRagDocumentServiceTests {

    @Test
    void disableDocumentsMarksDocumentsInactiveInsideTransaction() {
        FakeRagIndexRepository repository = new FakeRagIndexRepository();
        FakeVectorStore vectorStore = new FakeVectorStore();
        OfflineRagDocumentService service = new OfflineRagDocumentService(
                repository,
                vectorStore,
                new NoopTransactionManager()
        );

        int affected = service.disableDocuments(List.of("doc-1", "doc-2"));

        assertEquals(2, affected);
        assertEquals(List.of("doc-1", "doc-2"), repository.disabledDocumentIds);
    }

    @Test
    void enableDocumentsMarksDocumentsActiveInsideTransaction() {
        FakeRagIndexRepository repository = new FakeRagIndexRepository();
        FakeVectorStore vectorStore = new FakeVectorStore();
        OfflineRagDocumentService service = new OfflineRagDocumentService(
                repository,
                vectorStore,
                new NoopTransactionManager()
        );

        int affected = service.enableDocuments(List.of("doc-1", "doc-2"));

        assertEquals(2, affected);
        assertEquals(List.of("doc-1", "doc-2"), repository.enabledDocumentIds);
    }

    @Test
    void deleteDocumentsDeletesVectorIdsBeforeDatabaseRows() {
        FakeRagIndexRepository repository = new FakeRagIndexRepository();
        repository.vectorStoreIds = List.of("chunk-1", "chunk-2");
        FakeVectorStore vectorStore = new FakeVectorStore();
        OfflineRagDocumentService service = new OfflineRagDocumentService(
                repository,
                vectorStore,
                new NoopTransactionManager()
        );

        int affected = service.deleteDocuments(List.of("doc-1"));

        assertEquals(1, affected);
        assertEquals(List.of("chunk-1", "chunk-2"), vectorStore.deletedIds);
        assertEquals(List.of("doc-1"), repository.deletedChunkDocumentIds);
        assertEquals(List.of("doc-1"), repository.deletedDocumentIds);
    }

    private static class FakeVectorStore implements VectorStore {

        private final List<String> deletedIds = new ArrayList<>();

        @Override
        public void add(List<Document> documents) {
        }

        @Override
        public void delete(List<String> idList) {
            deletedIds.addAll(idList);
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

        private List<String> disabledDocumentIds = new ArrayList<>();
        private List<String> enabledDocumentIds = new ArrayList<>();
        private List<String> vectorStoreIds = new ArrayList<>();
        private List<String> deletedChunkDocumentIds = new ArrayList<>();
        private List<String> deletedDocumentIds = new ArrayList<>();

        FakeRagIndexRepository() {
            super(null, null, null, null);
        }

        @Override
        public int deactivateDocuments(List<String> documentIds) {
            disabledDocumentIds = new ArrayList<>(documentIds);
            return documentIds.size();
        }

        @Override
        public int activateDocuments(List<String> documentIds) {
            enabledDocumentIds = new ArrayList<>(documentIds);
            return documentIds.size();
        }

        @Override
        public List<String> findVectorStoreIdsByDocumentIds(List<String> documentIds) {
            return new ArrayList<>(vectorStoreIds);
        }

        @Override
        public int deleteChunksByDocumentIds(List<String> documentIds) {
            deletedChunkDocumentIds = new ArrayList<>(documentIds);
            return documentIds.size();
        }

        @Override
        public int deleteDocumentsByIds(List<String> documentIds) {
            deletedDocumentIds = new ArrayList<>(documentIds);
            return documentIds.size();
        }

        @Override
        public List<RagDocumentSummary> findDocuments() {
            return List.of(new RagDocumentSummary(
                    "doc-1",
                    "source",
                    DocumentType.TXT,
                    "v1",
                    "INDEXED",
                    true,
                    1,
                    OffsetDateTime.now(),
                    OffsetDateTime.now()
            ));
        }
    }

    private static class NoopTransactionManager implements PlatformTransactionManager {

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }
}
