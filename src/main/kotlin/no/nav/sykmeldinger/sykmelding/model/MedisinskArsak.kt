package no.nav.sykmeldinger.sykmelding.model

data class MedisinskArsak(
    val beskrivelse: String?,
    val arsak: List<MedisinskArsakType>
)
