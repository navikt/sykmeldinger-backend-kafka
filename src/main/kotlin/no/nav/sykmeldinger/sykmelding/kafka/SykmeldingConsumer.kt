package no.nav.sykmeldinger.sykmelding.kafka

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
import no.nav.sykmeldinger.pdl.error.PersonNotFoundInPdl
import no.nav.sykmeldinger.pdl.model.PdlPerson
import no.nav.sykmeldinger.pdl.service.PdlPersonService
import no.nav.sykmeldinger.sykmelding.SykmeldingMapper
import no.nav.sykmeldinger.sykmelding.SykmeldingService
import no.nav.sykmeldinger.sykmelding.model.Sykmeldt
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

private val objectMapper: ObjectMapper = jacksonObjectMapper().apply {
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
}

class SykmeldingConsumer(
    private val kafkaConsumer: KafkaConsumer<String, String>,
    private val topic: String,
    private val applicationState: ApplicationState,
    private val pdlPersonService: PdlPersonService,
    private val arbeidsforholdService: ArbeidsforholdService,
    private val sykmeldingService: SykmeldingService,
    private val cluster: String,
) {
    companion object {
        private val log = LoggerFactory.getLogger(SykmeldingConsumer::class.java)
    }

    private val oldSykmeldingTopics =
        listOf("privat-syfo-sm2013-automatiskBehandling", "privat-syfo-sm2013-manuellBehandling", "privat-syfo-sm2013-avvistBehandling")
    private var totalDuration = kotlin.time.Duration.ZERO
    private val sykmeldingDuration = kotlin.time.Duration.ZERO
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
                    kafkaConsumer.subscribe(listOf(topic))
                    consume()
                } catch (ex: Exception) {
                    log.error("error running sykmelding-consumer", ex)
                } finally {
                    kafkaConsumer.unsubscribe()
                    log.info("Unsubscribed from topic $topic and waiting for 10 seconds before trying again")
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
                    val sykmeldinger = consumerRecords.filter {
                        val headerValue = it.headers().headers("topic").first().value().toString(Charsets.UTF_8)
                        when (headerValue) {
                            "privat-syfo-sm2013-automatiskBehandling" -> okRecords++
                            "privat-syfo-sm2013-manuellBehandling" -> manuellRecords++
                            "privat-syfo-sm2013-avvistBehandling" -> avvistRecords++
                        }
                        oldSykmeldingTopics.contains(headerValue)
                    }.map { cr ->
                        val sykmelding: ReceivedSykmelding? =
                            cr.value()?.let { objectMapper.readValue(it, ReceivedSykmelding::class.java) }
                        cr.key() to sykmelding
                    }
                    sykmeldinger.forEach {
                        handleSykmelding(it.first, it.second)
                    }
                }
            }
        }
    }

    private suspend fun handleSykmelding(sykmeldingId: String, receivedSykmelding: ReceivedSykmelding?) {
        if (receivedSykmelding != null) {
            val sykmelding = SykmeldingMapper.mapToSykmelding(receivedSykmelding)
            try {
                val sykmeldt = pdlPersonService.getPerson(receivedSykmelding.personNrPasient, sykmeldingId).toSykmeldt()
                val arbeidsforhold = arbeidsforholdService.getArbeidsforhold(receivedSykmelding.personNrPasient)
                arbeidsforhold.forEach { arbeidsforholdService.insertOrUpdate(it) }
                sykmeldingService.saveOrUpdate(sykmeldingId, sykmelding, sykmeldt)
            } catch (e: PersonNotFoundInPdl) {
                if (cluster != "dev-gcp") {
                    log.error("Person not found in PDL, for sykmelding $sykmeldingId", e)
                    throw e
                } else {
                    log.warn("Person not found in PDL, for sykmelding $sykmeldingId, skipping in dev")
                }
            } catch (e: ClientRequestException) {
                if(cluster != "dev-gcp") {
                    log.error("Error doing request $sykmeldingId", e)
                    throw e
                } else {
                    log.warn("Error doing request $sykmeldingId, skipping in dev", e)
                }
            }
        } else {
            sykmeldingService.deleteSykmelding(sykmeldingId)
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
