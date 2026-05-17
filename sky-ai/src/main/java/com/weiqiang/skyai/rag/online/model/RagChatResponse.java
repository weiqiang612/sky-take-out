package com.weiqiang.skyai.rag.online.model;

import java.util.List;

public record RagChatResponse(
        String question,
        String answer,
        String context,
        List<RetrievedChunk> chunks
) {
}
