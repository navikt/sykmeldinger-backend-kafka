package no.nav.sykmeldinger.sykmelding.model

import java.time.LocalDate

data class KontaktMedPasient(
    val kontaktDato: LocalDate?,
    val begrunnelseIkkeKontakt: String?
)
