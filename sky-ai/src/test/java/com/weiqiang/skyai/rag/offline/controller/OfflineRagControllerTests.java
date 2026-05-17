package com.weiqiang.skyai.rag.offline.controller;

import com.weiqiang.skyai.rag.offline.index.OfflineIndexService;
import com.weiqiang.skyai.rag.offline.model.RagDocumentIdsRequest;
import com.weiqiang.skyai.rag.offline.model.RagDocumentOperationResponse;
import com.weiqiang.skyai.rag.offline.store.RagIndexRepository;
import com.weiqiang.skyai.rag.offline.service.OfflineRagDocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OfflineRagControllerTests {

    @Test
    void batchDisableReadsDocumentIdsPayload() {
        FakeDocumentService documentService = new FakeDocumentService();
        OfflineRagController controller = new OfflineRagController(
                new FakeOfflineIndexService(),
                documentService,
                new FakeRagIndexRepository()
        );

        RagDocumentOperationResponse response = controller.disableDocuments(new RagDocumentIdsRequest(List.of("doc-1", "doc-2")));

        assertEquals(List.of("doc-1", "doc-2"), documentService.lastDocumentIds);
        assertEquals("disable", response.operation());
        assertEquals(2, response.affectedCount());
    }

    @Test
    void batchEnableReadsDocumentIdsPayload() {
        FakeDocumentService documentService = new FakeDocumentService();
        OfflineRagController controller = new OfflineRagController(
                new FakeOfflineIndexService(),
                documentService,
                new FakeRagIndexRepository()
        );

        RagDocumentOperationResponse response = controller.enableDocuments(new RagDocumentIdsRequest(List.of("doc-9")));

        assertEquals(List.of("doc-9"), documentService.lastDocumentIds);
        assertEquals("enable", response.operation());
        assertEquals(1, response.affectedCount());
    }

    @Test
    void batchDeleteReadsDocumentIdsPayload() {
        FakeDocumentService documentService = new FakeDocumentService();
        OfflineRagController controller = new OfflineRagController(
                new FakeOfflineIndexService(),
                documentService,
                new FakeRagIndexRepository()
        );

        RagDocumentOperationResponse response = controller.deleteDocuments(new RagDocumentIdsRequest(List.of("doc-3")));

        assertEquals(List.of("doc-3"), documentService.lastDocumentIds);
        assertEquals("delete", response.operation());
        assertEquals(1, response.affectedCount());
    }

    private static class FakeOfflineIndexService extends OfflineIndexService {

        FakeOfflineIndexService() {
            super(null, null, null, null, null);
        }

        @Override
        public com.weiqiang.skyai.rag.offline.model.OfflineIndexResponse index(MultipartFile file, String documentType) {
            return null;
        }
    }

    private static class FakeDocumentService extends OfflineRagDocumentService {

        private List<String> lastDocumentIds = List.of();

        FakeDocumentService() {
            super(null, null, new NoopTransactionManager());
        }

        @Override
        public int disableDocuments(List<String> documentIds) {
            lastDocumentIds = documentIds;
            return documentIds.size();
        }

        @Override
        public int enableDocuments(List<String> documentIds) {
            lastDocumentIds = documentIds;
            return documentIds.size();
        }

        @Override
        public int deleteDocuments(List<String> documentIds) {
            lastDocumentIds = documentIds;
            return documentIds.size();
        }
    }

    private static class FakeRagIndexRepository extends RagIndexRepository {

        FakeRagIndexRepository() {
            super(null, null, null, null);
        }
    }

    private static class NoopTransactionManager implements org.springframework.transaction.PlatformTransactionManager {

        @Override
        public org.springframework.transaction.TransactionStatus getTransaction(org.springframework.transaction.TransactionDefinition definition) {
            return new org.springframework.transaction.support.SimpleTransactionStatus();
        }

        @Override
        public void commit(org.springframework.transaction.TransactionStatus status) {
        }

        @Override
        public void rollback(org.springframework.transaction.TransactionStatus status) {
        }
    }
}
