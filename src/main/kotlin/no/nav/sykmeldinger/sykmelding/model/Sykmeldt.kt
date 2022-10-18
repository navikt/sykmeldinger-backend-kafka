package no.nav.sykmeldinger.sykmelding.model

data class Sykmeldt(
    val fnr: String,
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String
)
