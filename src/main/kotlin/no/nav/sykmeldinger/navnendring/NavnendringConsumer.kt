package no.nav.sykmeldinger.navnendring

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.person.pdl.leesah.Personhendelse
import no.nav.sykmeldinger.application.ApplicationState
import no.nav.sykmeldinger.application.metrics.NL_NAVN_COUNTER
import no.nav.sykmeldinger.log
import no.nav.sykmeldinger.narmesteleder.db.NarmestelederDb
import no.nav.sykmeldinger.pdl.service.PdlPersonService
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration

class NavnendringConsumer(
    private val navnendringTopic: String,
    private val kafkaConsumer: KafkaConsumer<String, Personhendelse>,
    private val applicationState: ApplicationState,
    private val narmestelederDb: NarmestelederDb,
    private val pdlPersonService: PdlPersonService
) {
    @OptIn(DelicateCoroutinesApi::class)
    fun startConsumer() {
        GlobalScope.launch(Dispatchers.IO) {
            while (applicationState.ready) {
                try {
                    kafkaConsumer.subscribe(listOf(navnendringTopic))
                    consume()
                } catch (ex: Exception) {
                    log.error("error running navnendring-consumer", ex)
                } finally {
                    kafkaConsumer.unsubscribe()
                    log.info("Unsubscribed from topic $navnendringTopic and waiting for 10 seconds before trying again")
                    delay(10_000)
                }
            }
        }
    }

    private suspend fun consume() {
        while (applicationState.ready) {
            val records = kafkaConsumer.poll(Duration.ofSeconds(1)).mapNotNull { it.value() }
            if (records.isNotEmpty()) {
                records.forEach { personhendelse ->
                    handlePersonhendelse(personhendelse)
                }
            }
        }
    }

    suspend fun handlePersonhendelse(personhendelse: Personhendelse) {
        if (personhendelse.navn != null) {
            personhendelse.personidenter.forEach {
                if (narmestelederDb.isNarmesteleder(it)) {
                    log.info("Oppdaterer navn med navn fra PDL for nærmeste leder for personhendelse ${personhendelse.hendelseId}")
                    val person = pdlPersonService.getPerson(it, personhendelse.hendelseId)
                    narmestelederDb.updateNavn(
                        it,
                        person.navn.toFormattedNameString()
                    )
                    NL_NAVN_COUNTER.inc()
                }
            }
        }
    }
}
