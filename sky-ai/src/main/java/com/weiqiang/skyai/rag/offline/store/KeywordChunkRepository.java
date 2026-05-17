package com.weiqiang.skyai.rag.offline.store;

import com.weiqiang.skyai.rag.offline.model.KeywordSearchResult;

import java.util.List;

public interface KeywordChunkRepository {

    List<KeywordSearchResult> searchChunksByKeyword(String query, int topK);

    default List<String> findActiveDocumentIds() {
        return List.of();
    }
}
