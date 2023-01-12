CREATE SEQUENCE start_historikk_seq INCREMENT 50;

CREATE TABLE START_HISTORIKK
(
    HISTORIKK_ID       BIGINT primary key      not null,
    KJORINGS_TIMESTAMP TIMESTAMP with time zone not null,
    KJORINGS_AR        VARCHAR                  not null,
    TIMESTAMP          TIMESTAMP with time zone not null
);