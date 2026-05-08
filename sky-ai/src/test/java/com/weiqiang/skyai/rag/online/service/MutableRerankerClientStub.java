package com.weiqiang.skyai.rag.online.service;

import com.weiqiang.skyai.rag.online.model.RetrievedChunk;

import java.util.ArrayList;
import java.util.List;

class MutableRerankerClientStub implements RerankerClient {

    private List<RetrievedChunk> rerankedResults = List.of();

    void setRerankedResults(List<RetrievedChunk> rerankedResults) {
        this.rerankedResults = new ArrayList<>(rerankedResults);
    }

    @Override
    public List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates) {
        return new ArrayList<>(rerankedResults);
    }
}
