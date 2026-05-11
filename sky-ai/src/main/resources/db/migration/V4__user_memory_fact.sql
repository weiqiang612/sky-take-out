create table if not exists user_memory_fact (
    user_id varchar(255) not null,
    fact_key varchar(128) not null,
    fact_value text not null,
    source_type varchar(32) not null,
    confidence double precision null,
    updated_at timestamp not null,
    primary key (user_id, fact_key)
);
