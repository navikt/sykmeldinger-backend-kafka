package no.nav.sykmeldinger.arbeidsforhold.historisk

import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.sykmeldinger.application.ApplicationState
import no.nav.sykmeldinger.arbeidsforhold.ArbeidsforholdService
import no.nav.sykmeldinger.objectMapper
import no.nav.sykmeldinger.pdl.error.PersonNotFoundInPdl
import no.nav.sykmeldinger.pdl.model.PdlPerson
import no.nav.sykmeldinger.pdl.service.PdlPersonService
import no.nav.sykmeldinger.sykmelding.model.Sykmeldt
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class HistoriskSykmeldingConsumer(
    private val kafkaConsumer: KafkaConsumer<String, String>,
    private val applicationState: ApplicationState,
    private val pdlPersonService: PdlPersonService,
    private val arbeidsforholdService: ArbeidsforholdService,
    private val cluster: String,
) {
    companion object {
        private val log = LoggerFactory.getLogger(HistoriskSykmeldingConsumer::class.java)
        private const val OK_TOPIC = "teamsykmelding.ok-sykmelding"
        private const val AVVIST_TOPIC = "teamsykmelding.avvist-sykmelding"
        private const val MANUELL_TOPIC = "teamsykmelding.manuell-behandling-sykmelding"
    }

    private val sykmeldingTopics = listOf(OK_TOPIC, AVVIST_TOPIC, MANUELL_TOPIC)
    private var totalDuration = kotlin.time.Duration.ZERO
    private var totalRecords = 0
    private var okRecords = 0
    private var avvistRecords = 0
    private var manuellRecords = 0

    private var lastDate = OffsetDateTime.MIN

    @OptIn(DelicateCoroutinesApi::class)
    fun startConsumer() {
        GlobalScope.launch(Dispatchers.IO) {
            GlobalScope.launch(Dispatchers.IO) {
                while (applicationState.ready) {
                    no.nav.sykmeldinger.log.info(
                        "total: $totalRecords, ok: $okRecords, manuell: $manuellRecords, avvist: $avvistRecords, last $lastDate avg tot: ${
                        getDurationPerRecord(
                            totalDuration,
                            totalRecords
                        )
                        } ms"
                    )
                    delay(10000)
                }
            }
            while (applicationState.ready) {
                try {
                    kafkaConsumer.subscribe(sykmeldingTopics)
                    consume()
                } catch (ex: Exception) {
                    log.error("error running sykmelding-historisk-arbf-consumer", ex)
                } finally {
                    kafkaConsumer.unsubscribe()
                    log.info("Unsubscribed from topic $sykmeldingTopics and waiting for 10 seconds before trying again")
                    delay(10_000)
                }
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun consume() = withContext(Dispatchers.IO) {
        while (applicationState.ready) {

            val consumerRecords = kafkaConsumer.poll(Duration.ofSeconds(1))
            if (!consumerRecords.isEmpty) {
                totalDuration += measureTime {
                    totalRecords += consumerRecords.count()
                    lastDate = OffsetDateTime.ofInstant(
                        Instant.ofEpochMilli(consumerRecords.last().timestamp()),
                        ZoneOffset.UTC,
                    )

                    val sykmeldinger = consumerRecords.map { cr ->
                        val sykmelding: ReceivedSykmelding? = cr.value()?.let { objectMapper.readValue(it, ReceivedSykmelding::class.java) }
                        when (cr.topic()) {
                            OK_TOPIC -> okRecords++
                            MANUELL_TOPIC -> manuellRecords++
                            AVVIST_TOPIC -> avvistRecords++
                        }
                        cr.key() to sykmelding
                    }
                    if (lastDate.isBefore(OffsetDateTime.of(LocalDate.of(2022, 10, 28).atStartOfDay(), ZoneOffset.UTC))) {
                        sykmeldinger.forEach {
                            handleSykmelding(it.first, it.second)
                        }
                    }
                }
            }
        }
    }

    private suspend fun handleSykmelding(sykmeldingId: String, receivedSykmelding: ReceivedSykmelding?) {
        if (receivedSykmelding != null) {
            try {
                val sykmeldt = pdlPersonService.getPerson(receivedSykmelding.personNrPasient, sykmeldingId).toSykmeldt()
                val arbeidsforhold = arbeidsforholdService.getArbeidsforhold(sykmeldt.fnr)
                arbeidsforhold.forEach { arbeidsforholdService.insertOrUpdate(it) }
            } catch (e: PersonNotFoundInPdl) {
                if (cluster != "dev-gcp") {
                    log.error("Person not found in PDL, for sykmelding $sykmeldingId", e)
                    throw e
                } else {
                    log.warn("Person not found in PDL, for sykmelding $sykmeldingId, skipping in dev")
                }
            } catch (e: ClientRequestException) {
                if (cluster != "dev-gcp") {
                    log.error("Error doing request $sykmeldingId", e)
                    throw e
                } else {
                    log.warn("Error doing request $sykmeldingId, skipping in dev", e)
                }
            }
        }
    }

    private fun getDurationPerRecord(duration: kotlin.time.Duration, records: Int): Long {
        return when (duration.inWholeMilliseconds == 0L || records == 0) {
            false -> duration.div(records).inWholeMilliseconds
            else -> 0L
        }
    }
}

private fun PdlPerson.toSykmeldt(): Sykmeldt {
    return Sykmeldt(fnr = fnr, fornavn = navn.fornavn, mellomnavn = navn.mellomnavn, etternavn = navn.etternavn)
}
