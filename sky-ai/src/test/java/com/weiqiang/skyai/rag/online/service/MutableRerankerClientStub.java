package com.weiqiang.skyai.rag.online.service;

import com.weiqiang.skyai.rag.online.model.RetrievedChunk;

import java.util.ArrayList;
import java.util.List;

class MutableRerankerClientStub implements RerankerClient {

    private List<RetrievedChunk> rerankedResults = List.of();
    private RuntimeException failure;

    void setRerankedResults(List<RetrievedChunk> rerankedResults) {
        this.rerankedResults = new ArrayList<>(rerankedResults);
        this.failure = null;
    }

    void setFailure(RuntimeException failure) {
        this.failure = failure;
    }

    @Override
    public List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates) {
        if (failure != null) {
            throw failure;
        }
        return new ArrayList<>(rerankedResults);
    }
}
