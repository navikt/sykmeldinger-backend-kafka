CREATE TABLE narmesteleder
(
    narmeste_leder_id            VARCHAR primary key      not null,
    orgnummer                    VARCHAR                  not null,
    bruker_fnr                   VARCHAR                  not null,
    narmeste_leder_fnr           VARCHAR                  not null,
    narmeste_leder_telefonnummer VARCHAR                  not null,
    narmeste_leder_epost         VARCHAR                  not null,
    navn                         VARCHAR                  not null,
    arbeidsgiver_forskutterer    BOOLEAN,
    timestamp                    TIMESTAMP with time zone not null
);

create index narmeste_leder_fnr_idx on narmesteleder (bruker_fnr);