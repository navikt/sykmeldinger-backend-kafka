package no.nav.sykmeldinger.status.kafka

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.sykmeldinger.Environment
import no.nav.sykmeldinger.application.ApplicationState
import no.nav.sykmeldinger.status.db.SykmeldingStatusDB
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class SykmeldingStatusFixer(
    private val kafkaConsumer: KafkaConsumer<String, SykmeldingKafkaMessage?>,
    private val environment: Environment,
    private val sykmeldingStatusDb: SykmeldingStatusDB,
    private val applicationState: ApplicationState,
) {
    companion object {
        private val log = LoggerFactory.getLogger(SykmeldingStatusFixer::class.java)
    }

    private var duration = kotlin.time.Duration.ZERO
    private var totalRecords = 0
    private var updatedRecords = 0
    private var lastDate = OffsetDateTime.MIN
    private var lastOffset = mutableMapOf(
        0 to 0,
        1 to 0,
        2 to 0
    )

    private val maxOffsetBekreftet = mapOf(
        0 to 168733,
        1 to 168415,
        2 to 168824
    )

    @OptIn(DelicateCoroutinesApi::class)
    fun startConsumer() {
        GlobalScope.launch(Dispatchers.IO) {
            GlobalScope.launch(Dispatchers.IO) {
                while (applicationState.ready) {
                    log.info("total: $totalRecords, updated: $updatedRecords, currentOffsets $lastOffset last record was at $lastDate, avg ms: ${getDurationPerRecord()}ms")
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

    @OptIn(ExperimentalTime::class)
    private suspend fun consume() {
        while (applicationState.ready) {
            val records = kafkaConsumer.poll(Duration.ofSeconds(1)).filter { it.value() != null }
            if (records.isNotEmpty()) {
                val time = measureTime {
                    records.forEach {
                        if (it.offset() <= maxOffsetBekreftet[it.partition()]!!) {
                            val kafkaMessage = it.value()!!
                            val mottattTidspunkt = kafkaMessage.sykmelding.mottattTidspunkt
                            val statusTime = kafkaMessage.event.timestamp
                            if (statusTime.isBefore(mottattTidspunkt)) {
                                updateStatusTimstamp(
                                    kafkaMessage.sykmelding.id,
                                    statusTime,
                                    mottattTidspunkt,
                                    kafkaMessage.event.statusEvent
                                )
                                updatedRecords++
                            }
                        }
                        lastOffset[it.partition()] = it.offset().toInt()
                    }
                }
                duration += time
                totalRecords += records.count()
                lastDate = records.last().value()!!.event.timestamp
            }
        }
    }

    private fun getDurationPerRecord(): Long {
        return when (duration.inWholeMilliseconds == 0L || totalRecords == 0) {
            false -> duration.div(totalRecords).inWholeMilliseconds
            else -> 0L
        }
    }

    private fun updateStatusTimstamp(
        id: String,
        statusTime: OffsetDateTime,
        mottattTimestamp: OffsetDateTime,
        statusEvent: String
    ) {
        val adjustedTimestamp = adjustTimestamp(statusTime)
        val apenStatus = sykmeldingStatusDb.getFirstApenStatus(id) ?: throw java.lang.IllegalArgumentException("sykelding $id has no apen status")
        if (adjustedTimestamp.isBefore(mottattTimestamp) && adjustedTimestamp.isBefore(apenStatus)) {
            if (environment.cluster == "dev-gcp") {
                log.warn("Adjusted timestamp is before mottatt timestamp for sykmeldingId $id, ignoring in dev")
            } else {
                throw IllegalArgumentException("Adjusted timestamp is before mottatt timestamp for sykmeldingId $id")
            }
        }
        sykmeldingStatusDb.updateStatusTimestamp(id = id, adjustedTimestamp = adjustedTimestamp, statusTime = statusTime, statusEvent = statusEvent)
    }
}

fun adjustTimestamp(timestamp: OffsetDateTime): OffsetDateTime {
    return timestamp.atZoneSameInstant(ZoneId.of("Europe/Oslo")).toLocalDateTime().atOffset(ZoneOffset.UTC)
}
