package no.nav.sykmeldinger.pdl

import java.time.Duration
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.person.pdl.leesah.Endringstype
import no.nav.person.pdl.leesah.Personhendelse
import no.nav.sykmeldinger.application.ApplicationState
import no.nav.sykmeldinger.application.metrics.NL_NAVN_COUNTER
import no.nav.sykmeldinger.identendring.IdentendringService
import no.nav.sykmeldinger.log
import no.nav.sykmeldinger.narmesteleder.db.NarmestelederDb
import no.nav.sykmeldinger.pdl.service.PdlPersonService
import no.nav.sykmeldinger.secureLog
import org.apache.kafka.clients.consumer.KafkaConsumer

class PersonhendelseConsumer(
    private val navnendringTopic: String,
    private val kafkaConsumer: KafkaConsumer<String, Personhendelse>,
    private val applicationState: ApplicationState,
    private val narmestelederDb: NarmestelederDb,
    private val pdlPersonService: PdlPersonService,
    private val identendringService: IdentendringService,
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
            if (records.isNotEmpty()) {
                handlePersonhendelse(records)
            }
        }
    }

    suspend fun handlePersonhendelse(personhendelser: List<Personhendelse>) {
        personhendelser
            .filter { it.opplysningstype == "FOLKEREGISTERIDENTIFIKATOR_V1" }
            .filter {
                it.endringstype == Endringstype.KORRIGERT ||
                    it.endringstype == Endringstype.OPPRETTET
            }
            .map { it.personidenter }
            .toSet()
            .forEach {
                identendringService.updateIdent(it)
                secureLog.info("Mottok endring av IDer: $it")
            }

        personhendelser
            .filter { it.navn != null }
            .forEach { personhendelse ->
                personhendelse.personidenter.forEach {
                    if (narmestelederDb.isNarmesteleder(it)) {
                        log.info(
                            "Oppdaterer navn med navn fra PDL for nærmeste leder for personhendelse ${personhendelse.hendelseId}"
                        )
                        val person = pdlPersonService.getPerson(it, personhendelse.hendelseId)
                        narmestelederDb.updateNavn(
                            it,
                            person.navn.toFormattedNameString(),
                        )
                        NL_NAVN_COUNTER.inc()
                    }
                }
            }
    }
}
