package com.weiqiang.skyai.rag.offline.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "rag_chunk")
public class RagChunkEntity {

    @Id
    @Column(name = "chunk_id")
    private String chunkId;

    @Column(name = "chunk_hash")
    private String chunkHash;

    @Column(name = "document_id")
    private String documentId;

    @Column(name = "index_version")
    private String indexVersion;

    @Column(name = "chunk_index")
    private Integer chunkIndex;

    @Column(name = "content")
    private String content;

    @Column(name = "metadata_json")
    private String metadataJson;

    @Column(name = "vector_store_id")
    private String vectorStoreId;
}
