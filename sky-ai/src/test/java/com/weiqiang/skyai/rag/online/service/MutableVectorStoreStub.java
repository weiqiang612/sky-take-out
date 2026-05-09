package com.weiqiang.skyai.rag.online.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class MutableVectorStoreStub implements VectorStore {

    private List<Document> searchResults = List.of();
    private final Map<String, List<Document>> searchResultsByQuery = new LinkedHashMap<>();
    private final List<SearchRequest> searchRequests = new ArrayList<>();
    private SearchRequest lastSearchRequest;

    void setSearchResults(List<Document> searchResults) {
        this.searchResults = new ArrayList<>(searchResults);
        this.searchResultsByQuery.clear();
    }

    void setSearchResults(String query, List<Document> searchResults) {
        this.searchResultsByQuery.put(query, new ArrayList<>(searchResults));
    }

    SearchRequest getLastSearchRequest() {
        return lastSearchRequest;
    }

    List<SearchRequest> getSearchRequests() {
        return new ArrayList<>(searchRequests);
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
        this.searchRequests.add(request);
        return new ArrayList<>(searchResultsByQuery.getOrDefault(request.getQuery(), searchResults));
    }
}
