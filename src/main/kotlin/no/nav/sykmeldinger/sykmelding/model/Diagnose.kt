package no.nav.sykmeldinger.sykmelding.model

data class Diagnose(
    val kode: String,
    val system: String,
    val tekst: String?
)
