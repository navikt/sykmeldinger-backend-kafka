package no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.model

data class Opplysningspliktig(
    val identer: List<Ident>,
) {
    fun getJuridiskOrgnummer(): String {
        return identer.first {
            it.type == IdentType.ORGANISASJONSNUMMER
        }.ident
    }
}
