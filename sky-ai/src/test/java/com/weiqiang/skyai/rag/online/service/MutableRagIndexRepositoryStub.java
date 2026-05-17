package com.weiqiang.skyai.rag.online.service;

import com.weiqiang.skyai.rag.offline.model.KeywordSearchResult;
import com.weiqiang.skyai.rag.offline.store.RagIndexRepository;

import java.util.ArrayList;
import java.util.List;

class MutableRagIndexRepositoryStub extends RagIndexRepository {

    private List<String> activeDocumentIds = List.of();
    private List<KeywordSearchResult> keywordResults = List.of();
    private String lastQuery;
    private int lastTopK;

    MutableRagIndexRepositoryStub() {
        super(null, null, null, null);
    }

    void setActiveDocumentIds(List<String> activeDocumentIds) {
        this.activeDocumentIds = new ArrayList<>(activeDocumentIds);
    }

    void setKeywordResults(List<KeywordSearchResult> keywordResults) {
        this.keywordResults = new ArrayList<>(keywordResults);
    }

    String getLastQuery() {
        return lastQuery;
    }

    int getLastTopK() {
        return lastTopK;
    }

    @Override
    public List<String> findActiveDocumentIds() {
        return new ArrayList<>(activeDocumentIds);
    }

    @Override
    public void initializeSchema() {
        // No-op for unit tests.
    }

    @Override
    public List<KeywordSearchResult> searchChunksByKeyword(String query, int topK) {
        this.lastQuery = query;
        this.lastTopK = topK;
        return new ArrayList<>(keywordResults.stream().limit(topK).toList());
    }
}
