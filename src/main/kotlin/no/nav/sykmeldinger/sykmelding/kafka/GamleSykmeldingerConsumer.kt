package no.nav.sykmeldinger.sykmelding.kafka

import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.sykmeldinger.application.ApplicationState
import no.nav.sykmeldinger.arbeidsforhold.ArbeidsforholdService
import no.nav.sykmeldinger.behandlingsutfall.db.BehandlingsutfallDB
import no.nav.sykmeldinger.pdl.error.PersonNotFoundInPdl
import no.nav.sykmeldinger.pdl.service.PdlPersonService
import no.nav.sykmeldinger.sykmelding.SykmeldingMapper
import no.nav.sykmeldinger.sykmelding.SykmeldingService
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory
import java.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

data class SykmeldingMedBehandlingsutfall(
    val sykmelding: ReceivedSykmelding,
    val behandlingsutfall: Behandlingsutfall
)

data class Behandlingsutfall(
    val id: String,
    val behandlingsutfall: ValidationResult
)

class GamleSykmeldingerConsumer(
    private val gamleSykmeldingerConsumer: KafkaConsumer<String, SykmeldingMedBehandlingsutfall>,
    private val topic: String,
    private val applicationState: ApplicationState,
    private val sykmeldingService: SykmeldingService,
    private val pdlPersonService: PdlPersonService,
    private val arbeidsforholdService: ArbeidsforholdService,
    private val behandlingsutfallDB: BehandlingsutfallDB,
    private val cluster: String,
) {
    companion object {
        private val log = LoggerFactory.getLogger(GamleSykmeldingerConsumer::class.java)
    }

    private var totalDuration = kotlin.time.Duration.ZERO
    private var totalRecords = 0

    @OptIn(DelicateCoroutinesApi::class)
    fun startConsumer() {
        GlobalScope.launch(Dispatchers.IO) {
            GlobalScope.launch(Dispatchers.IO) {
                while (applicationState.ready) {
                    log.info(
                        "total: $totalRecords, avg tot: ${
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
                    gamleSykmeldingerConsumer.subscribe(listOf(topic))
                    consume()
                } catch (ex: Exception) {
                    log.error("error running gammel sykmelding-consumer", ex)
                } finally {
                    gamleSykmeldingerConsumer.unsubscribe()
                    log.info("Unsubscribed from topic $topic and waiting for 10 seconds before trying again")
                    delay(10_000)
                }
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun consume() = withContext(Dispatchers.IO) {
        while (applicationState.ready) {
            val consumerRecords = gamleSykmeldingerConsumer.poll(Duration.ofSeconds(1))
            if (!consumerRecords.isEmpty) {
                totalDuration += measureTime {
                    totalRecords += consumerRecords.count()
                    consumerRecords.forEach {
                        handleSykmelding(it.key(), it.value())
                    }
                }
            }
        }
    }

    private suspend fun handleSykmelding(
        sykmeldingId: String,
        sykmeldingMedBehandlingsutfall: SykmeldingMedBehandlingsutfall
    ) {
        val sykmelding = SykmeldingMapper.mapToSykmelding(sykmeldingMedBehandlingsutfall.sykmelding)
        try {
            val sykmeldt =
                pdlPersonService.getPerson(sykmeldingMedBehandlingsutfall.sykmelding.personNrPasient, sykmeldingId)
                    .toSykmeldt()
            val arbeidsforhold = arbeidsforholdService.getArbeidsforhold(sykmeldt.fnr)
            arbeidsforhold.forEach { arbeidsforholdService.insertOrUpdate(it) }
            val behandlingsutfall = sykmeldingMedBehandlingsutfall.behandlingsutfall.let {
                no.nav.sykmeldinger.behandlingsutfall.Behandlingsutfall(
                    sykmeldingId = it.id,
                    status = it.behandlingsutfall.status.name,
                    ruleHits = it.behandlingsutfall.ruleHits
                )
            }
            behandlingsutfallDB.insertOrUpdateBatch(listOf(behandlingsutfall))
            sykmeldingService.saveOrUpdate(sykmeldingId, sykmelding, sykmeldt)
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

    private fun getDurationPerRecord(duration: kotlin.time.Duration, records: Int): Long {
        return when (duration.inWholeMilliseconds == 0L || records == 0) {
            false -> duration.div(records).inWholeMilliseconds
            else -> 0L
        }
    }
}
