do $$
declare
    current_expr text;
begin
    select pg_get_expr(d.adbin, d.adrelid)
    into current_expr
    from pg_attribute a
    join pg_class c on c.oid = a.attrelid
    join pg_attrdef d on d.adrelid = a.attrelid and d.adnum = a.attnum
    where c.relname = 'rag_chunk'
      and a.attname = 'fts'
      and not a.attisdropped;

    if current_expr is null then
        return;
    end if;

    if current_expr ilike '%english%' and current_expr not ilike '%simple%' then
        execute 'drop index if exists idx_rag_chunk_fts';
        execute 'alter table rag_chunk drop column if exists fts';
        execute $sql$
            alter table rag_chunk
                add column fts tsvector generated always as (
                    to_tsvector(
                        'simple',
                        coalesce((metadata_json::jsonb ->> 'title'), '') || ' ' || coalesce(content, '')
                    )
                ) stored
        $sql$;
        execute 'create index if not exists idx_rag_chunk_fts on rag_chunk using gin (fts)';
    end if;
end $$;
