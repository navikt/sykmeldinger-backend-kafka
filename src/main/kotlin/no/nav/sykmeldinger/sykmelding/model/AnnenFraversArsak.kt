package no.nav.sykmeldinger.sykmelding.model

data class AnnenFraversArsak(
    val beskrivelse: String?,
    val grunn: List<AnnenFraverGrunn>,
)
