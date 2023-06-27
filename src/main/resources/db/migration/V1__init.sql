create table barnetrygdmottaker
(
    id       bigserial primary key not null,
    ident    varchar not null,
    ar       integer not null,
    created  timestamp with time zone not null default now()
);