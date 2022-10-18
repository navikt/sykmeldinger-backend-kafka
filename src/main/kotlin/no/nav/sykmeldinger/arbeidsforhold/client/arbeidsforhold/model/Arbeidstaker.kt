package no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.model

data class Arbeidstaker(
    val identer: List<Ident>
) {
    fun getFnr(): String {
        return identer.first {
            it.type == IdentType.FOLKEREGISTERIDENT && it.gjeldende
        }.ident
    }
}
