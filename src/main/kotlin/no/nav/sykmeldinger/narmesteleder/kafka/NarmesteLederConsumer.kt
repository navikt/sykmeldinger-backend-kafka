package no.nav.sykmeldinger.narmesteleder.kafka

import java.time.Duration
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

class NarmesteLederConsumer(
    private val environment: Environment,
    private val kafkaConsumer: KafkaConsumer<String, NarmestelederLeesahKafkaMessage>,
    private val narmesteLederService: NarmesteLederService,
    private val applicationState: ApplicationState,
) {

    @OptIn(DelicateCoroutinesApi::class)
    fun startConsumer() {
        GlobalScope.launch(Dispatchers.IO) {
            while (applicationState.ready) {
                try {
                    kafkaConsumer.subscribe(listOf(environment.narmestelederLeesahTopic))
                    consume()
                } catch (ex: Exception) {
                    log.error("error running nl-consumer", ex)
                } finally {
                    kafkaConsumer.unsubscribe()
                    log.info(
                        "Unsubscribed from topic ${environment.narmestelederLeesahTopic} and waiting for 10 seconds before trying again"
                    )
                    delay(10_000)
                }
            }
        }
    }

    private suspend fun consume() {
        while (applicationState.ready) {
            val records = kafkaConsumer.poll(Duration.ofSeconds(1)).mapNotNull { it.value() }
            if (records.isNotEmpty()) {
                records.forEach {
                    log.info(
                        "Mottatt nærmesteleder-oppdatering for kobling med id ${it.narmesteLederId}"
                    )
                    narmesteLederService.updateNarmesteLeder(it)
                }
            }
        }
    }
}
