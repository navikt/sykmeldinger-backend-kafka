CREATE TABLE sykmeldingstatus
(
    sykmelding_id   VARCHAR NOT NULL,
    event_timestamp TIMESTAMP   NOT NULL,
    event           VARCHAR     NOT NULL,

    CONSTRAINT sykmeldingstatus_pk PRIMARY KEY (sykmelding_id, event_timestamp)
);
