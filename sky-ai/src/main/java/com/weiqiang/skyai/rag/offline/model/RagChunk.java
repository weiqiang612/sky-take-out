package com.weiqiang.skyai.rag.offline.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * RAG文档块对象，包含块ID、文档ID、索引版本、文档类型、来源名称、块索引、内容和元数据等信息。
 * @param chunkId
 * @param documentId
 * @param indexVersion
 * @param documentType
 * @param sourceName
 * @param chunkIndex
 * @param content
 * @param metadata
 */
public record RagChunk(
        String chunkId,
        String documentId,
        String indexVersion,
        DocumentType documentType,
        String sourceName,
        int chunkIndex,
        String content,
        Map<String, Object> metadata
) {
    public static RagChunk of(ParsedDocument document, int chunkIndex, String content, Map<String, Object> metadata) {
        return new RagChunk(
                UUID.randomUUID().toString(),
                document.documentId(),
                document.indexVersion(),
                document.documentType(),
                document.sourceName(),
                chunkIndex,
                content,
                new LinkedHashMap<>(metadata)
        );
    }
}
