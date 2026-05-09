package com.weiqiang.skyai.rag.online.service;

class MutableQueryExpansionClientStub implements QueryExpansionClient {

    private String response = "[]";
    private RuntimeException failure;

    void setResponse(String response) {
        this.response = response;
        this.failure = null;
    }

    void setFailure(RuntimeException failure) {
        this.failure = failure;
    }

    @Override
    public String generate(String query, int maxQueries) {
        if (failure != null) {
            throw failure;
        }
        return response;
    }
}
