package com.weiqiang.skyai.rag.online.model;

public record OllamaGenerateResponse(
        String model,
        String response,
        Boolean done
) {
}
