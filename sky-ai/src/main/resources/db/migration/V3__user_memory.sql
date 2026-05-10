create table if not exists user_memory (
    user_id varchar(255) primary key,
    dietary_prefs text null,
    default_address text null,
    known_issues varchar(500) null,
    updated_at timestamp not null
);
