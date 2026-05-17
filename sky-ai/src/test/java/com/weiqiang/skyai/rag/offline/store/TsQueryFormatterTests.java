package com.weiqiang.skyai.rag.offline.store;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TsQueryFormatterTests {

    @Test
    void formatUsesLlmExtractedKeywordsWhenAvailable() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TsQueryFormatterTestConfiguration.class)) {
            MutableKeywordExtractionClientStub client = context.getBean(MutableKeywordExtractionClientStub.class);
            client.setKeywords(List.of("中文召回", "关键词提取", "pgvector"));

            TsQueryFormatter formatter = context.getBean(TsQueryFormatter.class);

            assertEquals("中文召回 关键词提取 pgvector", formatter.format("请帮我提取中文召回的关键词"));
        }
    }

    @Test
    void formatFallsBackToLocalTokenizationWhenLlmUnavailable() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TsQueryFormatterTestConfiguration.class)) {
            MutableKeywordExtractionClientStub client = context.getBean(MutableKeywordExtractionClientStub.class);
            client.setFailure(new IllegalStateException("boom"));

            TsQueryFormatter formatter = context.getBean(TsQueryFormatter.class);

            assertEquals("learning spring ai", formatter.format(" learning spring-ai "));
            assertEquals("中文 检索", formatter.format("中文 检索"));
            assertEquals("", formatter.format("   "));
        }
    }
}
