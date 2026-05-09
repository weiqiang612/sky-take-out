package com.weiqiang.skyai.rag.online.service;

import com.weiqiang.skyai.rag.offline.model.KeywordSearchResult;
import com.weiqiang.skyai.rag.offline.store.KeywordChunkRepository;

import java.util.ArrayList;
import java.util.List;

class MutableKeywordChunkRepositoryStub implements KeywordChunkRepository {

    private List<KeywordSearchResult> results = List.of();
    private String lastQuery;
    private int lastTopK;

    void setResults(List<KeywordSearchResult> results) {
        this.results = new ArrayList<>(results);
    }

    String getLastQuery() {
        return lastQuery;
    }

    int getLastTopK() {
        return lastTopK;
    }

    @Override
    public List<KeywordSearchResult> searchChunksByKeyword(String query, int topK) {
        this.lastQuery = query;
        this.lastTopK = topK;
        return new ArrayList<>(results.stream().limit(topK).toList());
    }
}
