package no.nav.sykmeldinger.arbeidsforhold.kafka.model

import java.time.LocalDateTime

data class ArbeidsforholdHendelse(
    val id: Long,
    val endringstype: Endringstype,
    val arbeidsforhold: ArbeidsforholdKafka,
    val entitetsendringer: List<Entitetsendring>,
    val tidsstempel: LocalDateTime
)
