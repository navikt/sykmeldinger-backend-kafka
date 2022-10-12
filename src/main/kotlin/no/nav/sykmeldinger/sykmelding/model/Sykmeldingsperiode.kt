package no.nav.sykmeldinger.sykmelding.model

import java.time.LocalDate

data class Sykmeldingsperiode(
    val fom: LocalDate,
    val tom: LocalDate,
    val gradert: Gradert?,
    val behandlingsdager: Int?,
    val innspillTilArbeidsgiver: String?,
    val type: Periodetype,
    val aktivitetIkkeMulig: AktivitetIkkeMulig?,
    val reisetilskudd: Boolean
)

enum class Periodetype {
    AKTIVITET_IKKE_MULIG,
    AVVENTENDE,
    BEHANDLINGSDAGER,
    GRADERT,
    REISETILSKUDD
}
