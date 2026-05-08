package com.weiqiang.skyai.rag.offline.model;

/**
 * 解析后的文档对象，包含文档ID、索引版本、文档类型、来源名称和内容等信息。
 * @param documentId
 * @param indexVersion
 * @param documentType
 * @param sourceName
 * @param content
 */
public record ParsedDocument(
        String documentId,
        String indexVersion,
        DocumentType documentType,
        String sourceName,
        String content
) {
}
