package no.nav.sykmeldinger.pdl.model

data class PdlPerson(
    val navn: Navn,
    val fnr: String,
    val oldFnr: List<String>,
)
