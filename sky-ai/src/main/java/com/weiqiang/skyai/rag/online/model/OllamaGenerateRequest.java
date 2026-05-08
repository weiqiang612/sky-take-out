package com.weiqiang.skyai.rag.online.model;

public record OllamaGenerateRequest(
        String model,
        String prompt,
        boolean stream,
        String format,
        String system
) {
}
