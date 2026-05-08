package com.weiqiang.skyai.rag.online.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.ArrayList;
import java.util.List;

class MutableVectorStoreStub implements VectorStore {

    private List<Document> searchResults = List.of();
    private SearchRequest lastSearchRequest;

    void setSearchResults(List<Document> searchResults) {
        this.searchResults = new ArrayList<>(searchResults);
    }

    SearchRequest getLastSearchRequest() {
        return lastSearchRequest;
    }

    @Override
    public void add(List<Document> documents) {
    }

    @Override
    public void delete(List<String> idList) {
    }

    @Override
    public void delete(Filter.Expression filterExpression) {
    }

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        this.lastSearchRequest = request;
        return new ArrayList<>(searchResults);
    }
}
