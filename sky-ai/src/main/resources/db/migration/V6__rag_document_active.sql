alter table if exists rag_document
    add column if not exists active boolean not null default true;
