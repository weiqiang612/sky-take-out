package com.weiqiang.skyai.rag.offline.model;

import java.util.List;

public record RagDocumentIdsRequest(
        List<String> documentIds
) {
}
