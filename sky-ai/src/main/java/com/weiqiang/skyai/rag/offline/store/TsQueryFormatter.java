package com.weiqiang.skyai.rag.offline.store;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class TsQueryFormatter {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{IsHan}\\p{L}\\p{Nd}_]+");
    private static final int MAX_KEYWORDS = 5;

    private final ObjectProvider<KeywordExtractionClient> keywordExtractionClientProvider;

    public String format(String rawQuery) {
        if (!StringUtils.hasText(rawQuery)) {
            return "";
        }

        List<String> keywords = extractKeywords(rawQuery);
        if (keywords == null || keywords.isEmpty()) {
            return "";
        }

        return keywords.stream()
                // 过滤掉空字符串
                .filter(StringUtils::hasText)
                .map(word -> word.trim()
                        // 移除掉所有 tsquery 的特殊控制符，防止用户或 LLM 输入恶意字符
                        .replaceAll("[|!&:<>()]+", " ")
                        .replaceAll("\\s+", " ")) // 内部空格保留为一个空格即可
                .collect(Collectors.joining(" ")); // 用空格连接，websearch 会自动处理为 AND

    }

    List<String> extractKeywords(String rawQuery) {
        KeywordExtractionClient client = keywordExtractionClientProvider.getIfAvailable();
        if (client != null) {
            try {
                List<String> extracted = client.extract(rawQuery, MAX_KEYWORDS);
                List<String> normalized = normalizeKeywords(extracted);
                if (!normalized.isEmpty()) {
                    log.info("LLM 关键词提取完成，query={}，keywords={}", rawQuery, normalized);
                    return normalized;
                }
            } catch (Exception ex) {
                log.warn("LLM 关键词提取失败，query={}，reason={}", rawQuery, ex.toString());
            }
        }

        List<String> fallback = normalizeKeywords(fallbackExtractKeywords(rawQuery));
        log.info("本地关键词提取完成，query={}，keywords={}", rawQuery, fallback);
        return fallback;
    }

    private List<String> fallbackExtractKeywords(String query) {
        List<String> keywords = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(query);
        while (matcher.find()) {
            String token = matcher.group().trim();
            if (!token.isBlank()) {
                keywords.add(token);
            }
        }
        if (keywords.isEmpty()) {
            keywords.add(query.trim());
        }
        return keywords;
    }

    private List<String> normalizeKeywords(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String keyword : keywords) {
            if (!StringUtils.hasText(keyword)) {
                continue;
            }
            String normalized = keyword.trim();
            if (!normalized.isBlank()) {
                unique.add(normalized);
            }
            if (unique.size() >= MAX_KEYWORDS) {
                break;
            }
        }
        return new ArrayList<>(unique);
    }
}
