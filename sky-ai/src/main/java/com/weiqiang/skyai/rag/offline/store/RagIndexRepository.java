package com.weiqiang.skyai.rag.offline.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiqiang.skyai.rag.offline.model.DocumentType;
import com.weiqiang.skyai.rag.offline.model.EmbeddedRagChunk;
import com.weiqiang.skyai.rag.offline.model.RagDocumentSummary;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class RagIndexRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void initializeSchema() {
        jdbcTemplate.execute("""
                create table if not exists rag_index_version (
                    index_version varchar(64) primary key,
                    embedding_model varchar(128),
                    chunking_summary text,
                    active boolean not null default true,
                    created_at timestamptz not null default now()
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists rag_document (
                    document_id varchar(64) primary key,
                    source_name varchar(512) not null,
                    document_type varchar(32) not null,
                    index_version varchar(64) not null,
                    content_hash varchar(64),
                    status varchar(32) not null,
                    chunk_count int not null default 0,
                    created_at timestamptz not null default now(),
                    updated_at timestamptz not null default now()
                )
                """);
        jdbcTemplate.execute("""
                alter table rag_document
                add column if not exists content_hash varchar(64)
                """);
        jdbcTemplate.execute("""
                create unique index if not exists ux_rag_document_content_hash
                on rag_document(content_hash)
                """);
        jdbcTemplate.execute("""
                create table if not exists rag_chunk (
                    chunk_id varchar(64) primary key,
                    chunk_hash varchar(64),
                    document_id varchar(64) not null references rag_document(document_id),
                    index_version varchar(64) not null,
                    chunk_index int not null,
                    content text not null,
                    metadata_json text not null,
                    vector_store_id varchar(64) not null,
                    created_at timestamptz not null default now(),
                    unique(document_id, index_version, chunk_index)
                )
                """);
        jdbcTemplate.execute("""
                alter table rag_chunk
                add column if not exists chunk_hash varchar(64)
                """);
        jdbcTemplate.execute("""
                create unique index if not exists ux_rag_chunk_chunk_hash
                on rag_chunk(chunk_hash)
                """);
    }

    public void saveIndexVersion(String indexVersion, String embeddingModel, String chunkingSummary) {
        jdbcTemplate.update("""
                insert into rag_index_version(index_version, embedding_model, chunking_summary, active)
                values (?, ?, ?, true)
                on conflict(index_version) do update set
                    embedding_model = excluded.embedding_model,
                    chunking_summary = excluded.chunking_summary
                """, indexVersion, embeddingModel, chunkingSummary);
    }

    public boolean saveDocumentIfAbsent(String documentId, String sourceName, DocumentType documentType,
                                        String indexVersion, String contentHash, int chunkCount, String status) {
        return jdbcTemplate.update("""
                insert into rag_document(document_id, source_name, document_type, index_version, content_hash, status, chunk_count)
                values (?, ?, ?, ?, ?, ?, ?)
                on conflict(content_hash) do nothing
                """, documentId, sourceName, documentType.name(), indexVersion, contentHash, status, chunkCount) == 1;
    }

    public Optional<RagDocumentSummary> findDocumentByContentHash(String contentHash) {
        List<RagDocumentSummary> documents = jdbcTemplate.query("""
                select document_id, source_name, document_type, index_version, status, chunk_count, created_at, updated_at
                from rag_document
                where content_hash = ?
                order by created_at desc
                limit 1
                """, this::mapDocument, contentHash);
        return documents.stream().findFirst();
    }

    public void updateDocumentStatus(String documentId, int chunkCount, String status) {
        jdbcTemplate.update("""
                update rag_document
                set status = ?, chunk_count = ?, updated_at = now()
                where document_id = ?
                """, status, chunkCount, documentId);
    }

    public void saveChunks(List<EmbeddedRagChunk> chunks) {
        for (EmbeddedRagChunk chunk : chunks) {
            saveChunk(chunk);
        }
    }

    public List<String> findChunkHashes() {
        return jdbcTemplate.query("""
                select chunk_hash
                from rag_chunk
                where chunk_hash is not null
                """, (rs, rowNum) -> rs.getString("chunk_hash"));
    }

    public List<RagDocumentSummary> findDocuments() {
        return jdbcTemplate.query("""
                select document_id, source_name, document_type, index_version, status, chunk_count, created_at, updated_at
                from rag_document
                order by created_at desc
                """, this::mapDocument);
    }

    private void saveChunk(EmbeddedRagChunk chunk) {
        jdbcTemplate.update("""
                insert into rag_chunk(chunk_id, chunk_hash, document_id, index_version, chunk_index, content, metadata_json, vector_store_id)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                on conflict(chunk_hash) do update set
                    chunk_id = excluded.chunk_id,
                    document_id = excluded.document_id,
                    index_version = excluded.index_version,
                    chunk_index = excluded.chunk_index,
                    content = excluded.content,
                    metadata_json = excluded.metadata_json,
                    vector_store_id = excluded.vector_store_id
                """, chunk.chunkId(), chunk.chunkHash(), chunk.documentId(), chunk.indexVersion(), chunk.chunkIndex(),
                chunk.content(), metadataJson(chunk), chunk.chunkId());
    }

    private RagDocumentSummary mapDocument(ResultSet rs, int rowNum) throws SQLException {
        return new RagDocumentSummary(
                rs.getString("document_id"),
                rs.getString("source_name"),
                DocumentType.valueOf(rs.getString("document_type")),
                rs.getString("index_version"),
                rs.getString("status"),
                rs.getInt("chunk_count"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private String metadataJson(EmbeddedRagChunk chunk) {
        try {
            java.util.Map<String, Object> metadata = new java.util.LinkedHashMap<>(chunk.metadata());
            metadata.put("chunkHash", chunk.chunkHash());
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize chunk metadata: " + chunk.chunkId(), ex);
        }
    }
}
