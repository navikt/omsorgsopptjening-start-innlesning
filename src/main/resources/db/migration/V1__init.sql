create extension if not exists "uuid-ossp";

create table innlesing
(
    id varchar primary key,
    år integer not null,
    forespurt_tidspunkt timestamptz not null default (now() at time zone 'utc'),
    start_tidspunkt timestamptz default null,
    ferdig_tidspunkt timestamptz default null
);

create table barnetrygdmottaker
(
    id uuid primary key default uuid_generate_v4(),
    ident varchar not null,
    opprettet timestamptz not null default (now() at time zone 'utc'),
    correlation_id varchar not null,
    request_id varchar not null references innlesing(id)
);

create table barnetrygdmottaker_status
(
    id uuid not null references barnetrygdmottaker(id),
    status json not null,
    statushistorikk json not null
);
