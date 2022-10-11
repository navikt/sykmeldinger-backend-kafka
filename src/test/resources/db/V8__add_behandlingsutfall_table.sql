CREATE TABLE behandlingsutfall
(
    sykmelding_id     varchar PRIMARY KEY,
    behandlingsutfall varchar NOT NULL,
    rule_hits         JSONB NULL
);
