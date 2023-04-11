package no.nav.sykmeldinger.narmesteleder.kafka

import no.nav.sykmeldinger.narmesteleder.db.NarmestelederDbModel
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class NarmestelederLeesahKafkaMessage(
    val narmesteLederId: UUID,
    val fnr: String,
    val orgnummer: String,
    val narmesteLederFnr: String,
    val narmesteLederTelefonnummer: String,
    val narmesteLederEpost: String,
    val aktivFom: LocalDate,
    val aktivTom: LocalDate?,
    val arbeidsgiverForskutterer: Boolean?,
    val timestamp: OffsetDateTime,
) {
    fun toNarmestelederDbModel(navn: String): NarmestelederDbModel {
        return NarmestelederDbModel(
            narmestelederId = narmesteLederId.toString(),
            orgnummer = orgnummer,
            brukerFnr = fnr,
            lederFnr = narmesteLederFnr,
            navn = navn,
            timestamp = timestamp,
        )
    }
}
