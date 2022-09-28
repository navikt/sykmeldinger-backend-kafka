package no.nav.sykmeldinger.status.kafka

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import no.nav.sykmeldinger.Environment
import no.nav.sykmeldinger.application.ApplicationState
import no.nav.sykmeldinger.application.db.DatabaseInterface
import no.nav.sykmeldinger.status.db.insertStatus
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.OffsetDateTime
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

class SykmeldingStatusConsumer(
    private val environment: Environment,
    private val kafkaConsumer: KafkaConsumer<String, SykmeldingStatusKafkaMessageDTO>,
    private val database: DatabaseInterface,
    private val applicationState: ApplicationState,
) {
    companion object {
        private val log = LoggerFactory.getLogger(SykmeldingStatusConsumer::class.java)
    }

    private var totalRecords = 0
    private var lastDate = OffsetDateTime.MIN

    @OptIn(DelicateCoroutinesApi::class)
    fun startConsumer() {
        GlobalScope.launch(Dispatchers.IO) {
            val loggerJob = GlobalScope.launch(Dispatchers.IO) {
                while (applicationState.ready) {
                    log.info("$totalRecords records processed, last record was at $lastDate")
                    delay(10000)
                }
            }
            while (applicationState.ready) {
                try {
                    kafkaConsumer.subscribe(listOf(environment.bekreftetTopic))
                    consume()
                } catch (ex: Exception) {
                    log.error("error running consumer", ex)
                } finally {
                    kafkaConsumer.unsubscribe()
                    log.info("Unsubscribed from topic ${environment.bekreftetTopic} and waiting for 10 seconds before trying again")
                    delay(10_000)
                }
            }
        }
    }

    private suspend fun consume() {
        while (applicationState.ready) {
            val records = kafkaConsumer.poll(Duration.ofSeconds(1)).mapNotNull { it.value() }
            if (records.isNotEmpty()) {
                lastDate = records.last().event.timestamp
                updateStatus(records.map { it.event })
            }
            totalRecords += records.count()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private suspend fun updateStatus(statusEvents: List<SykmeldingStatusKafkaEventDTO>) = withContext(Dispatchers.IO) {
        val chunks = statusEvents.chunked(10).map { chunk ->
            async(Dispatchers.IO) {
                database.insertStatus(chunk)
            }
        }
        chunks.awaitAll()
    }
}
