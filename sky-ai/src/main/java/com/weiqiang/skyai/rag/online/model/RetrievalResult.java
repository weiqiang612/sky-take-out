package com.weiqiang.skyai.rag.online.model;

import java.util.List;

public record RetrievalResult(
        String query,
        String context,
        List<RetrievedChunk> chunks
) {
}
