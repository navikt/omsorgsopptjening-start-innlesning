create table feilinfo
(
    id   uuid primary key default uuid_generate_v4(),
    time timestamptz not null,
    data varchar(65500)
);

create index feilinfo_time on feilinfo(time);