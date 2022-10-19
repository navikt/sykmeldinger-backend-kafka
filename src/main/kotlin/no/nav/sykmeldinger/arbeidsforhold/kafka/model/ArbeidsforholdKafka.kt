package no.nav.sykmeldinger.arbeidsforhold.kafka.model

data class ArbeidsforholdKafka(
    val navArbeidsforholdId: Int,
    val arbeidstaker: Arbeidstaker
)
