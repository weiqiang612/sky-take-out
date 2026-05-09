package com.weiqiang.skyai.rag.online.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiqiang.skyai.rag.online.config.OnlineRetrievalProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueryExpansionService {

    private final ObjectProvider<QueryExpansionClient> queryExpansionClientProvider;
    private final OnlineRetrievalProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * 执行查询扩展，返回扩展后的查询列表
     *
     * @param query 原始查询
     * @return 扩展后的查询列表，可能为空
     */
    public List<String> expand(String query) {
        OnlineRetrievalProperties.QueryExpansion config = properties.getQueryExpansion();
        if (!config.isEnabled() || config.getMaxQueries() <= 0 || !StringUtils.hasText(query)) {
            return List.of();
        }

        try {
            QueryExpansionClient client = queryExpansionClientProvider.getIfAvailable();
            if (client == null) {
                return List.of();
            }
            String content = client.generate(query, config.getMaxQueries());
            return parseQueries(content, query, config.getMaxQueries());
        } catch (Exception ex) {
            log.warn("查询扩展失败，query={}，fallbackOnFailure={}，reason={}",
                    query, config.isFallbackOnFailure(), ex.toString());
            if (config.isFallbackOnFailure()) {
                return List.of();
            }
            throw new IllegalStateException("Query expansion failed", ex);
        }
    }

    private List<String> parseQueries(String content, String originalQuery, int maxQueries) throws Exception {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }

        String json = extractJsonArray(content);
        List<String> parsed = objectMapper.readValue(json, new TypeReference<List<String>>() {
        });
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String item : parsed) {
            if (!StringUtils.hasText(item)) {
                continue;
            }
            String normalized = item.trim();
            if (!normalized.equalsIgnoreCase(originalQuery.trim())) {
                unique.add(normalized);
            }
            // 只保留前maxQueries个有效的扩展查询，避免过多无效查询影响后续检索效率
            if (unique.size() >= maxQueries) {
                break;
            }
        }
        return new ArrayList<>(unique);
    }

    private String extractJsonArray(String content) {
        int start = content.indexOf('[');
        int end = content.lastIndexOf(']');
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("Query expansion response is not a JSON array");
        }
        return content.substring(start, end + 1);
    }
}
