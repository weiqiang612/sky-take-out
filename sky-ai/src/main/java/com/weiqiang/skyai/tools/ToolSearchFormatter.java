package com.weiqiang.skyai.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
class ToolSearchFormatter {

    private final ObjectMapper objectMapper;

    String format(String source, String query, List<SearchCandidate> candidates) {
        SearchResult result = new SearchResult(
                source,
                query,
                candidates != null && !candidates.isEmpty(),
                requiresConfirmation(candidates),
                candidates == null ? 0 : candidates.size(),
                candidates == null ? List.of() : candidates
        );
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException ex) {
            return "{\"source\":\"" + source + "\",\"query\":\"" + escape(query) + "\",\"matched\":false,\"needConfirm\":false,\"candidateCount\":0,\"candidates\":[]}";
        }
    }

    SearchCandidate candidate(Long id, String title, String summary, String matchBy, double confidence) {
        return new SearchCandidate(id, title, summary, matchBy, confidence);
    }

    private boolean requiresConfirmation(List<SearchCandidate> candidates) {
        return candidates == null || candidates.size() != 1 || candidates.get(0).confidence() < 0.95;
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    record SearchCandidate(Long id, String title, String summary, String matchBy, double confidence) {
    }

    record SearchResult(String source, String query, boolean matched, boolean needConfirm, int candidateCount,
                        List<SearchCandidate> candidates) {
    }
}
