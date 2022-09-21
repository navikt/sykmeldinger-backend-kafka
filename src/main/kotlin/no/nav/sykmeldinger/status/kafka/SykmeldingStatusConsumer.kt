package no.nav.sykmeldinger.status.kafka

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import no.nav.sykmeldinger.Environment
import no.nav.sykmeldinger.application.ApplicationState
import no.nav.sykmeldinger.application.db.DatabaseInterface
import no.nav.sykmeldinger.status.db.insertStatus
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.OffsetDateTime
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay

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
                    kafkaConsumer.subscribe(listOf(environment.statusTopic))
                    consume()
                } catch (ex: Exception) {
                    log.error("error running consumer", ex)
                } finally {
                    kafkaConsumer.unsubscribe()
                    log.info("Unsubscribed from topic ${environment.statusTopic} and waiting for 10 seconds before trying again")
                    delay(10_000)
                }
            }
        }
    }

    private fun consume() {
        while (applicationState.ready) {
            val records = kafkaConsumer.poll(Duration.ofSeconds(1)).mapNotNull { it.value() }
            records.forEach {
                updateStatus(it)
                lastDate = it.kafkaMetadata.timestamp
            }
            totalRecords += records.count()
        }
    }

    private fun updateStatus(it: SykmeldingStatusKafkaMessageDTO) {
        database.insertStatus(it.event.sykmeldingId, it.event.statusEvent, it.event.timestamp)
    }
}