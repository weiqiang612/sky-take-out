package com.weiqiang.skyai.rag.offline.model;

import java.util.List;
import java.util.Map;

/**
 * 嵌入后的RAG文档块，包含原始块信息和对应的向量表示。
 * @param chunkId
 * @param documentId
 * @param indexVersion
 * @param documentType
 * @param sourceName
 * @param chunkIndex
 * @param content
 * @param embedding
 * @param metadata
 */
public record EmbeddedRagChunk(
        String chunkId,
        String chunkHash,
        String documentId,
        String indexVersion,
        DocumentType documentType,
        String sourceName,
        int chunkIndex,
        String content,
        List<Double> embedding,
        Map<String, Object> metadata
) {
    public static EmbeddedRagChunk from(RagChunk chunk, String chunkHash, List<Double> embedding) {
        return new EmbeddedRagChunk(
                chunk.chunkId(),
                chunkHash,
                chunk.documentId(),
                chunk.indexVersion(),
                chunk.documentType(),
                chunk.sourceName(),
                chunk.chunkIndex(),
                chunk.content(),
                embedding,
                chunk.metadata()
        );
    }
}
