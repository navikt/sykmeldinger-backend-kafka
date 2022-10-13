package no.nav.sykmeldinger.sykmelding.model

data class SporsmalSvar(
    val sporsmal: String?,
    val svar: String,
    val restriksjoner: List<SvarRestriksjon>
)
