package no.nav.sykmeldinger.status.kafka

import java.sql.BatchUpdateException
import java.time.Duration
import java.time.OffsetDateTime
import kotlin.time.measureTime
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

    private var duration = kotlin.time.Duration.ZERO
    private var totalRecords = 0
    private var lastDate = OffsetDateTime.MIN

    @OptIn(DelicateCoroutinesApi::class)
    fun startConsumer() {
        GlobalScope.launch(Dispatchers.IO) {
            GlobalScope.launch(Dispatchers.IO) {
                while (applicationState.ready) {
                    log.info(
                        "$totalRecords records processed, last record was at $lastDate avg time per record: ${getDurationPerRecord()} ms"
                    )
                    delay(10000)
                }
            }
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

    private fun getDurationPerRecord(): Long {
        return when (duration.inWholeMilliseconds == 0L || totalRecords == 0) {
            false -> duration.div(totalRecords).inWholeMilliseconds
            else -> 0L
        }
    }

    private suspend fun consume() {
        while (applicationState.ready) {
            val records = kafkaConsumer.poll(Duration.ofSeconds(1)).mapNotNull { it.value() }
            if (records.isNotEmpty()) {
                lastDate = records.last().event.timestamp
                val time = measureTime { updateStatus(records.map { it.event }) }
                duration += time
                totalRecords += records.count()
            }
        }
    }

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
