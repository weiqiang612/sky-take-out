package com.weiqiang.skyai.rag.offline.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ChatClientKeywordExtractionClient implements KeywordExtractionClient {

    private static final String SYSTEM_PROMPT = """
            你是 RAG 检索的关键词提取器。
            请从用户问题中提取最适合 PostgreSQL pgvector / FTS 检索的核心关键词或短语。
            要求：
            1. 只返回 JSON 字符串数组，不要解释。
            2. 最多返回 {maxKeywords} 个关键词。
            3. 优先保留中文专有名词、英文术语、接口名、类名、编号、版本号和业务实体名。
            4. 去掉语气词、停用词、重复词和无意义修饰语。
            5. 不要输出完整句子，只输出适合检索的核心词组。
            """;

    private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;
    private final ObjectMapper objectMapper;

    @Override
    public List<String> extract(String query, int maxKeywords) {
        if (!StringUtils.hasText(query) || maxKeywords <= 0) {
            return List.of();
        }

        ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
        if (builder == null) {
            return List.of();
        }

        String content = builder.build()
                .prompt()
                .system(s -> s.text(SYSTEM_PROMPT).param("maxKeywords", maxKeywords))
                .user(query)
                .call()
                .content();

        return parseKeywords(content, maxKeywords, query);
    }

    private List<String> parseKeywords(String content, int maxKeywords, String originalQuery) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }

        String json = extractJsonArray(content);
        try {
            List<String> parsed = objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
            LinkedHashSet<String> unique = new LinkedHashSet<>();
            for (String item : parsed) {
                if (!StringUtils.hasText(item)) {
                    continue;
                }
                String normalized = item.trim();
                if (!normalized.isBlank() && !normalized.equalsIgnoreCase(originalQuery.trim())) {
                    unique.add(normalized);
                }
                if (unique.size() >= maxKeywords) {
                    break;
                }
            }
            return new ArrayList<>(unique);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse keyword extraction response", ex);
        }
    }

    private String extractJsonArray(String content) {
        int start = content.indexOf('[');
        int end = content.lastIndexOf(']');
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("Keyword extraction response is not a JSON array");
        }
        return content.substring(start, end + 1);
    }
}
