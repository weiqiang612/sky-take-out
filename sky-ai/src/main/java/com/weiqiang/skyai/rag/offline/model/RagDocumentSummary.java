package com.weiqiang.skyai.rag.offline.model;

import java.time.OffsetDateTime;

/**
 * RAG文档摘要对象，包含文档ID、来源名称、文档类型、索引版本、状态、块数量和创建/更新时间等信息。
 * @param documentId
 * @param sourceName
 * @param documentType
 * @param indexVersion
 * @param status
 * @param active
 * @param chunkCount
 * @param createdAt
 * @param updatedAt
 */
public record RagDocumentSummary(
        String documentId,
        String sourceName,
        DocumentType documentType,
        String indexVersion,
        String status,
        boolean active,
        int chunkCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
