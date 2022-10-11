CREATE TABLE arbeidsforhold
(
    fnr                VARCHAR not null,
    orgnummer          VARCHAR not null,
    juridisk_orgnummer VARCHAR not null,
    orgnavn            VARCHAR not null,
    fom                DATE    not null,
    tom                DATE,

    CONSTRAINT arbeidsforhold_pk PRIMARY KEY (fnr, orgnummer)
);