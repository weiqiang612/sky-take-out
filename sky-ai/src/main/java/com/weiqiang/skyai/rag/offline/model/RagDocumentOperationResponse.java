package com.weiqiang.skyai.rag.offline.model;

import java.util.List;

public record RagDocumentOperationResponse(
        String operation,
        List<String> documentIds,
        int affectedCount
) {
}
