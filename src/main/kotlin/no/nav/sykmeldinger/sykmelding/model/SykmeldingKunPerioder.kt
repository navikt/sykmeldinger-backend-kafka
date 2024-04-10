package no.nav.sykmeldinger.sykmelding.model

import java.time.LocalDate
import java.time.OffsetDateTime

data class SykmeldingKunPerioder(
    val mottattTidspunkt: OffsetDateTime,
    val sykmeldingsperioder: List<KunPeriode>,
)

data class KunPeriode(
    val fom: LocalDate,
    val tom: LocalDate,
)
