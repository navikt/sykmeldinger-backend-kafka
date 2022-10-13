package no.nav.sykmeldinger.sykmelding.model

data class Behandler(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
    val adresse: Adresse,
    val tlf: String?
)
