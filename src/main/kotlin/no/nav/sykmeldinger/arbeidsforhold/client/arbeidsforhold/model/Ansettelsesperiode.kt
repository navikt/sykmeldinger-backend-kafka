package no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.model

import java.time.LocalDate

data class Ansettelsesperiode(
    val startdato: LocalDate,
    val sluttdato: LocalDate?
)
