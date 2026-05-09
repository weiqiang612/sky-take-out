create table if not exists rag_index_version (
    index_version varchar(64) primary key,
    embedding_model varchar(128),
    chunking_summary text,
    active boolean not null default true,
    created_at timestamptz not null default now()
);

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
);

alter table if exists rag_document
    add column if not exists content_hash varchar(64);

create unique index if not exists ux_rag_document_content_hash
    on rag_document(content_hash);

create table if not exists rag_chunk (
    chunk_id varchar(64) primary key,
    chunk_hash varchar(64),
    document_id varchar(64) not null references rag_document(document_id),
    index_version varchar(64) not null,
    chunk_index int not null,
    content text not null,
    metadata_json text not null,
    vector_store_id varchar(64) not null,
    fts tsvector generated always as (
        to_tsvector(
                'simple',
                coalesce((metadata_json::jsonb ->> 'title'), '') || ' ' || coalesce(content, '')
        )
    ) stored,
    created_at timestamptz not null default now(),
    unique(document_id, index_version, chunk_index)
);

alter table if exists rag_chunk
    add column if not exists chunk_hash varchar(64);

alter table if exists rag_chunk
    add column if not exists fts tsvector generated always as (
        to_tsvector(
                'simple',
                coalesce((metadata_json::jsonb ->> 'title'), '') || ' ' || coalesce(content, '')
        )
    ) stored;

create unique index if not exists ux_rag_chunk_chunk_hash
    on rag_chunk(chunk_hash);

create index if not exists idx_rag_chunk_fts
    on rag_chunk using gin (fts);
