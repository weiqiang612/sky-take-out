package com.weiqiang.skyai.rag.offline.service;

import com.weiqiang.skyai.rag.offline.store.RagIndexRepository;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OfflineRagDocumentService {

    private final RagIndexRepository repository;
    private final VectorStore vectorStore;
    private final TransactionTemplate transactionTemplate;

    public OfflineRagDocumentService(RagIndexRepository repository,
                                     VectorStore vectorStore,
                                     PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.vectorStore = vectorStore;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public int disableDocuments(List<String> documentIds) {
        List<String> ids = normalize(documentIds);
        if (ids.isEmpty()) {
            return 0;
        }
        Integer updated = transactionTemplate.execute(status -> repository.deactivateDocuments(ids));
        return updated == null ? 0 : updated;
    }

    public int enableDocuments(List<String> documentIds) {
        List<String> ids = normalize(documentIds);
        if (ids.isEmpty()) {
            return 0;
        }
        Integer updated = transactionTemplate.execute(status -> repository.activateDocuments(ids));
        return updated == null ? 0 : updated;
    }

    public int deleteDocuments(List<String> documentIds) {
        List<String> ids = normalize(documentIds);
        if (ids.isEmpty()) {
            return 0;
        }

        List<String> vectorStoreIds = repository.findVectorStoreIdsByDocumentIds(ids);
        if (!vectorStoreIds.isEmpty()) {
            vectorStore.delete(vectorStoreIds);
        }

        Integer deleted = transactionTemplate.execute(status -> {
            repository.deleteChunksByDocumentIds(ids);
            return repository.deleteDocumentsByIds(ids);
        });
        return deleted == null ? 0 : deleted;
    }

    private List<String> normalize(List<String> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> unique = documentIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new ArrayList<>(unique);
    }
}
