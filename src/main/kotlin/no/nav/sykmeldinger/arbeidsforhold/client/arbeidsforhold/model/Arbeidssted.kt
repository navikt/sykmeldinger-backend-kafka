package no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.model

data class Arbeidssted(
    val type: ArbeidsstedType,
    val identer: List<Ident>,
) {
    fun getOrgnummer(): String {
        return identer.first {
            it.type == IdentType.ORGANISASJONSNUMMER
        }.ident
    }
}

enum class ArbeidsstedType {
    Underenhet, Person
}
