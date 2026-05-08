package com.weiqiang.skyai.rag.online.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record SiliconFlowRerankResponse(
        String id,
        List<Result> results,
        Meta meta
) {

    public record Result(
            int index,
            Document document,
            @JsonProperty("relevance_score") Double relevanceScore
    ) {
    }

    public record Document(
            String text
    ) {
    }

    public record Meta(
            Tokens tokens
    ) {
    }

    public record Tokens(
            @JsonProperty("input_tokens") Integer inputTokens,
            @JsonProperty("output_tokens") Integer outputTokens,
            @JsonProperty("image_tokens") Integer imageTokens
    ) {
    }
}
