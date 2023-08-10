create extension if not exists "uuid-ossp";

create table barnetrygdmottaker
(
    id uuid primary key default uuid_generate_v4(),
    ident varchar not null,
    ar integer not null,
    opprettet  timestamptz not null default (now() at time zone 'utc'),
    correlation_id varchar not null
);

create table barnetrygdmottaker_status
(
    id uuid not null references barnetrygdmottaker(id),
    status json not null,
    statushistorikk json not null
);
