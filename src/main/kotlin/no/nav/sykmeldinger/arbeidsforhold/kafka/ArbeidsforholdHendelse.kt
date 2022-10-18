package no.nav.sykmeldinger.arbeidsforhold.kafka

import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.model.AaregArbeidsforhold

data class ArbeidsforholdHendelse(
    val id: Long,
    val endringstype: Endringstype,
    val arbeidsforhold: AaregArbeidsforhold
)

enum class Endringstype {
    Opprettelse, Endring, Sletting
}
