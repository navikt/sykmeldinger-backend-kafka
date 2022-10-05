package no.nav.sykmeldinger.narmesteleder.kafka

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.sykmeldinger.Environment
import no.nav.sykmeldinger.application.ApplicationState
import no.nav.sykmeldinger.log
import no.nav.sykmeldinger.narmesteleder.NarmesteLederService
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration
import java.time.OffsetDateTime

class NarmesteLederConsumer(
    private val environment: Environment,
    private val kafkaConsumer: KafkaConsumer<String, NarmestelederLeesahKafkaMessage>,
    private val narmesteLederService: NarmesteLederService,
    private val applicationState: ApplicationState
) {
    private var totalRecords = 0
    private var lastDate = OffsetDateTime.MIN

    @OptIn(DelicateCoroutinesApi::class)
    fun startConsumer() {
        GlobalScope.launch(Dispatchers.IO) {
            GlobalScope.launch(Dispatchers.IO) {
                while (applicationState.ready) {
                    log.info("$totalRecords nl-records processed, last record was at $lastDate")
                    delay(10000)
                }
            }
            while (applicationState.ready) {
                try {
                    kafkaConsumer.subscribe(listOf(environment.narmestelederLeesahTopic))
                    consume()
                } catch (ex: Exception) {
                    log.error("error running nl-consumer", ex)
                } finally {
                    kafkaConsumer.unsubscribe()
                    log.info("Unsubscribed from topic ${environment.narmestelederLeesahTopic} and waiting for 10 seconds before trying again")
                    delay(10_000)
                }
            }
        }
    }

    private suspend fun consume() {
        while (applicationState.ready) {
            val records = kafkaConsumer.poll(Duration.ofSeconds(1)).mapNotNull { it.value() }
            if (records.isNotEmpty()) {
                lastDate = records.last().timestamp
                records.forEach {
                    narmesteLederService.updateNarmesteLeder(it)
                }
                totalRecords += records.count()
            }
        }
    }
}
