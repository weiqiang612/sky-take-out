package com.weiqiang.skyai.rag.online.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record SiliconFlowRerankRequest(
        String model,
        String query,
        List<String> documents,
        @JsonProperty("top_n") Integer topN,
        @JsonProperty("return_documents") Boolean returnDocuments
) {
}
