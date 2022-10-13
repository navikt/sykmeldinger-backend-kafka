package no.nav.sykmeldinger.sykmelding.model

import java.time.LocalDate

data class ErIkkeIArbeid(
    val arbeidsforPaSikt: Boolean,
    val arbeidsforFOM: LocalDate?,
    val vurderingsdato: LocalDate?
)
