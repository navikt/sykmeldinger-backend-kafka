package no.nav.sykmeldinger.sykmelding.model

data class Adresse(
    val gate: String?,
    val postnummer: Int?,
    val kommune: String?,
    val postboks: String?,
    val land: String?
)
