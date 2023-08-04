package no.nav.sykmeldinger.arbeidsforhold.kafka.model

import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.model.Ident
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.model.IdentType

data class Arbeidstaker(
    val identer: List<Ident>,
) {
    fun getFnr(): String {
        return identer.first { it.type == IdentType.FOLKEREGISTERIDENT && it.gjeldende }.ident
    }
}
