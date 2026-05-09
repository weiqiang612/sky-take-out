package com.weiqiang.skyai.rag.offline.store;

import java.util.List;

class MutableKeywordExtractionClientStub implements KeywordExtractionClient {

    private List<String> keywords = List.of();
    private RuntimeException failure;

    void setKeywords(List<String> keywords) {
        this.keywords = keywords;
        this.failure = null;
    }

    void setFailure(RuntimeException failure) {
        this.failure = failure;
    }

    @Override
    public List<String> extract(String query, int maxKeywords) {
        if (failure != null) {
            throw failure;
        }
        return keywords;
    }
}
