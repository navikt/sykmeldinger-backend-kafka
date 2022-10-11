package no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.model

data class Ident(
    val type: IdentType,
    val ident: String
)

enum class IdentType {
    AKTORID, FOLKEREGISTERIDENT, ORGANISASJONSNUMMER
}
