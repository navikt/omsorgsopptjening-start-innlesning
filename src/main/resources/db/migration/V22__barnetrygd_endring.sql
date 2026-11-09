create table barnetrygd_endring
(
    id bigserial primary key,
    ident varchar(11) not null,
    vedtakstidspunkt timestamp not null,
    endringstidspunkt date not null,
    sekvensnummer bigint not null unique,
    opprettet timestamptz not null
);