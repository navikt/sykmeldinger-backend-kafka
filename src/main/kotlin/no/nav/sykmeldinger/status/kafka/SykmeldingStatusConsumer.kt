package no.nav.sykmeldinger.status.kafka

import io.opentelemetry.instrumentation.annotations.WithSpan
import java.sql.BatchUpdateException
import java.time.Duration
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import no.nav.sykmeldinger.Environment
import no.nav.sykmeldinger.application.ApplicationState
import no.nav.sykmeldinger.status.db.SykmeldingStatusDB
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory

class SykmeldingStatusConsumer(
    private val environment: Environment,
    private val kafkaConsumer: KafkaConsumer<String, SykmeldingStatusKafkaMessageDTO>,
    private val database: SykmeldingStatusDB,
    private val applicationState: ApplicationState,
) {
    companion object {
        private val log = LoggerFactory.getLogger(SykmeldingStatusConsumer::class.java)
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun startConsumer() {
        GlobalScope.launch(Dispatchers.IO) {
            while (applicationState.ready) {
                try {
                    kafkaConsumer.subscribe(listOf(environment.statusTopic))
                    consume()
                } catch (ex: Exception) {
                    log.error("error running consumer", ex)
                } finally {
                    kafkaConsumer.unsubscribe()
                    log.info(
                        "Unsubscribed from topic ${environment.statusTopic} and waiting for 10 seconds before trying again"
                    )
                    delay(10_000)
                }
            }
        }
    }

    private suspend fun consume() {
        while (applicationState.ready) {
            val records =
                kafkaConsumer
                    .poll(Duration.ofSeconds(1))
                    .mapNotNull { it.value() }
                    .filter { it.kafkaMetadata.source != "sykmeldinger-backend" }
            if (records.isNotEmpty()) {
                updateStatus(records.map { it.event })
            }
        }
    }

    @WithSpan
    private suspend fun updateStatus(statusEvents: List<SykmeldingStatusKafkaEventDTO>) =
        withContext(Dispatchers.IO) {
            while (!tryUpdateStatus(statusEvents)) {
                delay(100)
                log.info("waiting and trying to update status")
            }
        }

    private fun tryUpdateStatus(statusEvents: List<SykmeldingStatusKafkaEventDTO>): Boolean {
        return try {
            database.insertStatus(statusEvents)
            true
        } catch (ex: BatchUpdateException) {
            false
        }
    }
}
