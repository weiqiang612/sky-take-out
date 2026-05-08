package com.weiqiang.skyai.rag.offline.model;

/**
 * 离线索引响应对象，包含文档ID、索引版本、文档类型、来源名称、块数量和处理状态等信息。
 * @param documentId
 * @param indexVersion
 * @param documentType
 * @param sourceName
 * @param chunkCount
 * @param status
 */
public record OfflineIndexResponse(
        String documentId,
        String indexVersion,
        DocumentType documentType,
        String sourceName,
        int chunkCount,
        String status
) {
}
