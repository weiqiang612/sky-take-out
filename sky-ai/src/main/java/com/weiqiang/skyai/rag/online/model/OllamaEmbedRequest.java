package com.weiqiang.skyai.rag.online.model;

import java.util.List;

public record OllamaEmbedRequest(
        String model,
        List<String> input,
        boolean truncate
) {
}
