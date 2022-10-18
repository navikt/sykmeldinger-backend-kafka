package no.nav.sykmeldinger.arbeidsforhold.kafka.model

data class ArbeidsforholdHendelse(
    val id: Long,
    val endringstype: Endringstype,
    val arbeidsforhold: ArbeidsforholdKafka
)
