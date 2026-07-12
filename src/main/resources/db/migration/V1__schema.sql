-- Schema initial : clients d'API et fichiers. Flyway est la source de verite du schema (ddl-auto: none).

create table api_client (
    id            uuid primary key,
    name          text        not null,
    api_key_hash  text        not null unique,
    active        boolean     not null,
    created_at    timestamptz not null
);

create table file (
    id            uuid        primary key,
    owner_id      uuid        not null,
    batch_id      uuid,
    filename      text        not null,
    content_type  text        not null,
    size_bytes    bigint      not null,
    storage_key   text        not null,
    status        varchar(20) not null,
    scan_attempts integer     not null default 0,
    scan_engine   text,
    scan_infected boolean,
    threat_name   text,
    created_at    timestamptz not null,
    updated_at    timestamptz not null,
    scanned_at    timestamptz
);

create index idx_file_owner        on file (owner_id);
create index idx_file_owner_status on file (owner_id, status);
create index idx_file_batch        on file (batch_id);
create index idx_file_storage_key  on file (storage_key);
