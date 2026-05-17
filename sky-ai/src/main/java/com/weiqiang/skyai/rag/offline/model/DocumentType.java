package com.weiqiang.skyai.rag.offline.model;

import java.util.Locale;

/**
 * 文档类型枚举，支持QA、Markdown、PDF和TXT四种类型。
 * <p>
 * - QA类型适用于结构化的问答数据，通常以JSONL或QA后缀的文件形式存在，每行表示一个问题和答案的对。
 * - Markdown类型适用于以Markdown格式编写的文档，支持.md或.markdown后缀的文件。
 * - PDF类型适用于以PDF格式存在的文档，后缀为.pdf。
 * - TXT类型适用于纯文本格式的文档，后缀为.txt。
 * <p>
 */
public enum DocumentType {
    QA,
    MARKDOWN,
    PDF,
    TXT;

    public static DocumentType fromNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return DocumentType.valueOf(value.trim().replace("-", "_").toUpperCase(Locale.ROOT));
    }

    public static DocumentType infer(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jsonl") || lower.endsWith(".qa")) {
            return QA;
        }
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) {
            return MARKDOWN;
        }
        if (lower.endsWith(".pdf")) {
            return PDF;
        }
        if (lower.endsWith(".txt")) {
            return TXT;
        }
        return null;
    }
}
