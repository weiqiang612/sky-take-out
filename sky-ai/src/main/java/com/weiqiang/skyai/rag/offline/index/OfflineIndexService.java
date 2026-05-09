package com.weiqiang.skyai.rag.offline.index;

import com.weiqiang.skyai.rag.offline.chunk.ChunkingStrategy;
import com.weiqiang.skyai.rag.offline.chunk.ChunkingStrategyRegistry;
import com.weiqiang.skyai.rag.offline.model.DocumentType;
import com.weiqiang.skyai.rag.offline.model.EmbeddedRagChunk;
import com.weiqiang.skyai.rag.offline.model.OfflineIndexResponse;
import com.weiqiang.skyai.rag.offline.model.ParsedDocument;
import com.weiqiang.skyai.rag.offline.model.RagChunk;
import com.weiqiang.skyai.rag.offline.model.RagDocumentSummary;
import com.weiqiang.skyai.rag.offline.parse.DocumentParser;
import com.weiqiang.skyai.rag.offline.store.RagIndexRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
@RequiredArgsConstructor
public class OfflineIndexService {

    private static final DateTimeFormatter VERSION_FORMATTER = DateTimeFormatter.ofPattern("'v'yyyyMMdd-HHmmss");

    private final AtomicInteger versionCounter = new AtomicInteger();
    private final DocumentParser documentParser;
    private final ChunkingStrategyRegistry strategyRegistry;
    private final EmbeddingModel embeddingModel;
    private final VectorStore vectorStore;
    private final RagIndexRepository repository;

    @Value("${spring.ai.ollama.embedding.options.model:${spring.ai.openai.embedding.options.model:unknown}}")
    private String embeddingModelName;

    public OfflineIndexResponse index(MultipartFile file, String documentTypeValue) {
        // 1. 先解析文档，基于解析后的正文计算去重指纹
        DocumentType explicitType = DocumentType.fromNullable(documentTypeValue);
        String documentId = UUID.randomUUID().toString();
        String indexVersion = nextVersion();
        ParsedDocument document = documentParser.parse(file, explicitType, documentId, indexVersion);
        String contentHash = sha256Hex(normalizeContent(document.content()));

        RagDocumentSummary existingDocument = repository.findDocumentByContentHash(contentHash).orElse(null);
        if (existingDocument != null) {
            return toDuplicateResponse(existingDocument);
        }

        boolean inserted = repository.saveDocumentIfAbsent(documentId, document.sourceName(), document.documentType(),
                indexVersion, contentHash, 0, "PROCESSING");
        if (!inserted) {
            RagDocumentSummary existing = repository.findDocumentByContentHash(contentHash)
                    .orElseThrow(() -> new IllegalStateException("Duplicate document detected but no record found: "
                            + document.sourceName()));
            return toDuplicateResponse(existing);
        }

        // 2. 根据文档类型选择解析和分块策略
        ChunkingStrategy strategy = strategyRegistry.get(document.documentType());
        try {
            // 3. 分块、嵌入
            List<RagChunk> chunks = strategy.chunk(document);
            List<EmbeddedRagChunk> embeddedChunks = filterAndEmbed(chunks);

            if (embeddedChunks.isEmpty()) {
                repository.updateDocumentStatus(documentId, 0, "DUPLICATE_SKIPPED");
                return new OfflineIndexResponse(documentId, indexVersion, document.documentType(), document.sourceName(),
                        0, "DUPLICATE_SKIPPED");
            }

            // 4. 保存索引版本、文档信息、分块信息，并将嵌入后的分块添加到向量数据库
            repository.saveIndexVersion(indexVersion, embeddingModelName, "strategy=" + document.documentType());
            repository.saveChunks(embeddedChunks);
            vectorStore.add(embeddedChunks.stream().map(chunk -> toVectorDocument(chunk, contentHash)).toList());
            repository.updateDocumentStatus(documentId, embeddedChunks.size(), "INDEXED");
            log.info("离线索引写入完成，documentId={}，indexVersion={}，chunkCount={}，rag_chunk=已写入，vectorStore=已写入",
                    documentId, indexVersion, embeddedChunks.size());
            // 5. 返回索引结果
            return new OfflineIndexResponse(documentId, indexVersion, document.documentType(), document.sourceName(),
                    embeddedChunks.size(), "INDEXED");
        } catch (RuntimeException ex) {
            repository.updateDocumentStatus(documentId, 0, "FAILED");
            throw ex;
        }
    }

    private String nextVersion() {
        int number = versionCounter.updateAndGet(i -> i >= 999 ? 1 : i + 1);
        return VERSION_FORMATTER.format(LocalDateTime.now()) + "-" + String.format("%03d", number);
    }

    String normalizeContent(String content) {
        if (content == null) {
            return "";
        }
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        StringBuilder builder = new StringBuilder(normalized.length());
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(lines[i].stripTrailing());
        }
        return builder.toString().trim();
    }

    String normalizeChunkContent(String content) {
        String normalized = normalizeContent(content).toLowerCase(Locale.ROOT);
        return normalized.replaceAll("\\s+", " ").trim();
    }

    String sha256Hex(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is not available", ex);
        }
    }

    private List<EmbeddedRagChunk> filterAndEmbed(List<RagChunk> chunks) {
        Set<String> existingChunkHashes = new HashSet<>(repository.findChunkHashes());
        Set<String> seenChunkHashes = new HashSet<>();
        List<RagChunk> uniqueChunks = new ArrayList<>();
        List<String> uniqueChunkHashes = new ArrayList<>();
        for (RagChunk chunk : chunks) {
            String chunkHash = sha256Hex(normalizeChunkContent(chunk.content()));
            if (!seenChunkHashes.add(chunkHash)) {
                continue;
            }
            if (existingChunkHashes.contains(chunkHash)) {
                continue;
            }
            uniqueChunks.add(chunk);
            uniqueChunkHashes.add(chunkHash);
        }
        if (uniqueChunks.isEmpty()) {
            return List.of();
        }
        return embed(uniqueChunks, uniqueChunkHashes);
    }

    private List<EmbeddedRagChunk> embed(List<RagChunk> chunks, List<String> chunkHashes) {
        List<String> texts = chunks.stream().map(RagChunk::content).toList();
        List<float[]> embeddings = embeddingModel.embed(texts);
        List<EmbeddedRagChunk> result = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            result.add(EmbeddedRagChunk.from(chunks.get(i), chunkHashes.get(i), toDoubleList(embeddings.get(i))));
        }
        return result;
    }

    private List<Double> toDoubleList(float[] values) {
        List<Double> doubles = new ArrayList<>(values.length);
        for (float value : values) {
            doubles.add((double) value);
        }
        return doubles;
    }

    private Document toVectorDocument(EmbeddedRagChunk chunk, String contentHash) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        chunk.metadata().forEach((key, value) -> {
            if (value != null) {
                metadata.put(key, value);
            }
        });
        metadata.put("chunkHash", chunk.chunkHash());
        metadata.put("contentHash", contentHash);
        metadata.put("documentId", chunk.documentId());
        metadata.put("indexVersion", chunk.indexVersion());
        metadata.put("documentType", chunk.documentType().name());
        metadata.put("sourceName", chunk.sourceName());
        metadata.put("chunkIndex", chunk.chunkIndex());
        return Document.builder()
                .id(chunk.chunkId())
                .text(chunk.content())
                .metadata(metadata)
                .build();
    }

    private OfflineIndexResponse toDuplicateResponse(RagDocumentSummary existing) {
        return new OfflineIndexResponse(existing.documentId(), existing.indexVersion(), existing.documentType(),
                existing.sourceName(), existing.chunkCount(), "DUPLICATE_SKIPPED");
    }
}
