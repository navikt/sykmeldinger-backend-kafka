package no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.model

data class Ident(
    val type: IdentType,
    val ident: String,
    val gjeldende: Boolean,
)

enum class IdentType {
    AKTORID,
    FOLKEREGISTERIDENT,
    ORGANISASJONSNUMMER
}
