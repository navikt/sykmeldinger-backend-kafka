package no.nav.sykmeldinger.pdl

import java.time.Duration
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.person.pdl.leesah.Personhendelse
import no.nav.sykmeldinger.application.ApplicationState
import no.nav.sykmeldinger.log
import no.nav.sykmeldinger.pdl.service.PersonhendelseService
import org.apache.kafka.clients.consumer.KafkaConsumer

class PersonhendelseConsumer(
    private val navnendringTopic: String,
    private val kafkaConsumer: KafkaConsumer<String, Personhendelse>,
    private val applicationState: ApplicationState,
    private val personhendlseService: PersonhendelseService,
) {
    @OptIn(DelicateCoroutinesApi::class)
    fun startConsumer() {
        GlobalScope.launch(Dispatchers.IO) {
            while (applicationState.ready) {
                try {
                    kafkaConsumer.subscribe(listOf(navnendringTopic))
                    consume()
                } catch (ex: Exception) {
                    log.error("error running pdl-consumer", ex)
                } finally {
                    kafkaConsumer.unsubscribe()
                    log.info(
                        "Unsubscribed from topic $navnendringTopic and waiting for 10 seconds before trying again"
                    )
                    delay(10_000)
                }
            }
        }
    }

    private suspend fun consume() {
        while (applicationState.ready) {
            val records = kafkaConsumer.poll(Duration.ofSeconds(5)).mapNotNull { it.value() }
                .filter { it.hendelseId != "0316768f-b8fa-464f-8e65-29e9794b3627" }
            if (records.isNotEmpty()) {
                personhendlseService.handlePersonhendelse(records.map { it.toDataClass() })
            }
        }
    }
}
