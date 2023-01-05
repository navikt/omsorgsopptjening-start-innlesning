CREATE TABLE START_HISTORIKK
(
    HISTORIKK_ID        VARCHAR primary key      not null,
    KJORINGS_TIMESTAMP  VARCHAR                  not null,
    KJORINGS_AR         VARCHAR                  not null,
    TIMESTAMP           TIMESTAMP with time zone not null
)