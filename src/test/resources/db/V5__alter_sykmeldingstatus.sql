alter table sykmeldingstatus
    add column arbeidsgiver jsonb null,
    add column sporsmal jsonb null;
