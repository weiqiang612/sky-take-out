package com.weiqiang.skyai.rag.online.model;

import java.util.List;

public record OllamaEmbedResponse(
        String model,
        List<List<Double>> embeddings
) {
}
