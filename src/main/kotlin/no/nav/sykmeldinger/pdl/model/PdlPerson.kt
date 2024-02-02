package no.nav.sykmeldinger.pdl.model

import java.time.LocalDate

data class PdlPerson(
    val navn: Navn,
    val fnr: String,
    val oldFnr: List<String>,
    val foedselsdato: LocalDate?,
)
