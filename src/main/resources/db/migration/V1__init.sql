create table barnetrygdmottaker
(
    id       bigserial primary key not null,
    ident    varchar not null,
    ar       integer not null,
    opprettet  timestamptz not null default (now() at time zone 'utc'),
    prosessert boolean not null default false
);