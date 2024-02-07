package no.nav.sykmeldinger.sykmelding.model

import java.time.LocalDate

data class Sykmeldt(
    val fnr: String,
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
    val foedselsdato: LocalDate?,
)
