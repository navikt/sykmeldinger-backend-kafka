package no.nav.sykmeldinger.sykmelding.kafka

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.plugins.ClientRequestException
import java.time.Duration
import java.time.LocalDate
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

private val objectMapper: ObjectMapper =
    jacksonObjectMapper().apply {
        registerModule(JavaTimeModule())
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
    }

class SykmeldingConsumer(
    private val kafkaConsumer: KafkaConsumer<String, String>,
    private val applicationState: ApplicationState,
    private val pdlPersonService: PdlPersonService,
    private val arbeidsforholdService: ArbeidsforholdService,
    private val sykmeldingService: SykmeldingService,
    private val cluster: String,
) {
    companion object {
        private val log = LoggerFactory.getLogger(SykmeldingConsumer::class.java)
        private const val OK_TOPIC = "teamsykmelding.ok-sykmelding"
        private const val AVVIST_TOPIC = "teamsykmelding.avvist-sykmelding"
        private const val MANUELL_TOPIC = "teamsykmelding.manuell-behandling-sykmelding"
    }

    private val sykmeldingTopics = listOf(OK_TOPIC, AVVIST_TOPIC, MANUELL_TOPIC)

    @OptIn(DelicateCoroutinesApi::class)
    fun startConsumer() {
        GlobalScope.launch(Dispatchers.IO) {
            while (applicationState.ready) {
                try {
                    kafkaConsumer.subscribe(sykmeldingTopics)
                    consume()
                } catch (ex: Exception) {
                    log.error("error running sykmelding-consumer", ex)
                } finally {
                    kafkaConsumer.unsubscribe()
                    log.info(
                        "Unsubscribed from topic $sykmeldingTopics and waiting for 10 seconds before trying again"
                    )
                    delay(10_000)
                }
            }
        }
    }

    private suspend fun consume() =
        withContext(Dispatchers.IO) {
            while (applicationState.ready) {
                val consumerRecords = kafkaConsumer.poll(Duration.ofSeconds(1))
                if (!consumerRecords.isEmpty) {
                    consumerRecords.forEach { cr ->
                        val sykmelding: ReceivedSykmelding? =
                            cr.value()?.let {
                                objectMapper.readValue(it, ReceivedSykmelding::class.java)
                            }

                        handleSykmelding(cr.key(), sykmelding)
                    }
                }
            }
        }

    private suspend fun handleSykmelding(
        sykmeldingId: String,
        receivedSykmelding: ReceivedSykmelding?,
    ) {
        if (receivedSykmelding != null) {
            val sykmelding = SykmeldingMapper.mapToSykmelding(receivedSykmelding)
            try {
                val sykmeldt =
                    pdlPersonService
                        .getPerson(receivedSykmelding.personNrPasient, sykmeldingId)
                        .toSykmeldt()
                val (fom, tom) = getReceivedSykmeldingFomTom(receivedSykmelding)
                val arbeidsforhold = arbeidsforholdService.getArbeidsforhold(sykmeldt.fnr, fom, tom)
                arbeidsforhold.forEach { arbeidsforholdService.insertOrUpdate(it) }
                sykmeldingService.saveOrUpdate(
                    sykmeldingId,
                    sykmelding,
                    sykmeldt,
                    receivedSykmelding.validationResult
                )
            } catch (e: PersonNotFoundInPdl) {
                if (cluster != "dev-gcp") {
                    log.error("Person not found in PDL, for sykmelding $sykmeldingId", e)
                    throw e
                } else {
                    log.warn(
                        "Person not found in PDL, for sykmelding $sykmeldingId, skipping in dev"
                    )
                }
            } catch (e: ClientRequestException) {
                if (cluster != "dev-gcp") {
                    log.error("Error doing request $sykmeldingId", e)
                    throw e
                } else {
                    log.warn("Error doing request $sykmeldingId, skipping in dev", e)
                }
            }
        } else {
            sykmeldingService.deleteSykmelding(sykmeldingId)
            log.info("Deleted sykmelding etc with sykmeldingId: $sykmeldingId")
        }
    }

    private fun getReceivedSykmeldingFomTom(
        receivedSykmelding: ReceivedSykmelding
    ): Pair<LocalDate, LocalDate> {
        val fom = receivedSykmelding.sykmelding.perioder.first().fom
        val tom = receivedSykmelding.sykmelding.perioder.last().tom
        return fom to tom
    }
}

fun PdlPerson.toSykmeldt(): Sykmeldt {
    return Sykmeldt(
        fnr = fnr,
        fornavn = navn.fornavn,
        mellomnavn = navn.mellomnavn,
        etternavn = navn.etternavn,
        foedselsdato = foedselsdato
    )
}
