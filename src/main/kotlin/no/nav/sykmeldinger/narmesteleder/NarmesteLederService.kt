package no.nav.sykmeldinger.narmesteleder

import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.sykmeldinger.application.metrics.NL_TOPIC_COUNTER
import no.nav.sykmeldinger.log
import no.nav.sykmeldinger.narmesteleder.db.NarmestelederDb
import no.nav.sykmeldinger.narmesteleder.kafka.NarmestelederLeesahKafkaMessage
import no.nav.sykmeldinger.pdl.service.PdlPersonService

class NarmesteLederService(
    private val pdlPersonService: PdlPersonService,
    private val narmestelederDb: NarmestelederDb,
    private val cluster: String,
) {

    @WithSpan
    suspend fun updateNarmesteLeder(
        narmestelederLeesahKafkaMessage: NarmestelederLeesahKafkaMessage
    ) {
        Span.current()
            .setAttribute(
                "narmesteLederId",
                narmestelederLeesahKafkaMessage.narmesteLederId.toString(),
            )

        when (narmestelederLeesahKafkaMessage.aktivTom) {
            null -> {
                try {
                    val pdlPerson =
                        pdlPersonService.getPerson(
                            ident = narmestelederLeesahKafkaMessage.narmesteLederFnr,
                            callId = narmestelederLeesahKafkaMessage.narmesteLederId.toString(),
                        )
                    narmestelederDb.insertOrUpdate(
                        narmestelederLeesahKafkaMessage.toNarmestelederDbModel(
                            pdlPerson.navn.toFormattedNameString(),
                        ),
                    )
                    NL_TOPIC_COUNTER.labels("ny").inc()
                } catch (e: Exception) {
                    log.error(
                        "Noe gikk galt ved oppdatering av nærmeste leder med id ${narmestelederLeesahKafkaMessage.narmesteLederId}",
                        e,
                    )
                    if (cluster == "dev-gcp") {
                        log.info(
                            "Ignorerer feil i dev for id ${narmestelederLeesahKafkaMessage.narmesteLederId}",
                        )
                    } else {
                        throw e
                    }
                }
            }
            else -> {
                narmestelederDb.remove(narmestelederLeesahKafkaMessage.narmesteLederId.toString())
                NL_TOPIC_COUNTER.labels("avbrutt").inc()
            }
        }
    }
}
