package no.nav.sykmeldinger.sykmelding.model

data class Prognose(
    val arbeidsforEtterPeriode: Boolean,
    val hensynArbeidsplassen: String?,
    val erIArbeid: ErIArbeid?,
    val erIkkeIArbeid: ErIkkeIArbeid?,
)
