package no.nav.sykmeldinger.sykmelding.model

data class ArbeidsrelatertArsak(
    val beskrivelse: String?,
    val arsak: List<ArbeidsrelatertArsakType>,
)
