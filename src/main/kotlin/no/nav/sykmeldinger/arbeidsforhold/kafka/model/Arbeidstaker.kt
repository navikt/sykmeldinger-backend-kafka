package no.nav.sykmeldinger.arbeidsforhold.kafka.model

import com.fasterxml.jackson.annotation.JsonIgnore
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.model.Ident
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.model.IdentType

data class Arbeidstaker(
    val identer: List<Ident>?,
) {
    @JsonIgnore
    fun getFnr(): String {
        return identer?.first { it.type == IdentType.FOLKEREGISTERIDENT && it.gjeldende }?.ident
            ?: throw IllegalArgumentException("Identer mangler fnr")
    }
}
