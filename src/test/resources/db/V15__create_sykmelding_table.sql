create table sykmelding (
    sykmelding_id VARCHAR primary key not null,
    fnr VARCHAR not null,
    sykmelding jsonb not null
);

create index sykmelding_fnr_idx on sykmelding (fnr);