package no.nav.sykmeldinger.arbeidsforhold.model

import java.time.LocalDate

data class Arbeidsforhold(
    val id: Int,
    val fnr: String,
    val orgnummer: String,
    val juridiskOrgnummer: String,
    val orgNavn: String,
    val fom: LocalDate,
    val tom: LocalDate?,
)
