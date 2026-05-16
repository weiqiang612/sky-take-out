alter table if exists user_memory_fact
    add column if not exists created_at timestamp not null default now();

create table if not exists user_memory_fact_history
(
    user_id    varchar(255) not null,
    fact_key   varchar(128) not null,
    old_value  text,
    new_value  text,
    source_type varchar(32) not null,
    confidence double precision null,
    changed_at timestamp not null,
    primary key (user_id, fact_key, changed_at)
);
