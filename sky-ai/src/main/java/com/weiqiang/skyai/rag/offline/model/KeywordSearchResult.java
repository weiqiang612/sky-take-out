package com.weiqiang.skyai.rag.offline.model;

import java.util.Map;

public record KeywordSearchResult(
        String content,
        Map<String, Object> metadata,
        double score
) {
}
