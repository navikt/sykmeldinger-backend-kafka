CREATE TABLE sykmeldingstatus
(
    sykmelding_id VARCHAR    NOT NULL,
    timestamp     timestamptz NOT NULL,
    event         VARCHAR    NOT NULL,

    CONSTRAINT sykmeldingstatus_pk PRIMARY KEY (sykmelding_id, timestamp)
);
