package no.nav.sykmeldinger.identendring

import java.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.person.pdl.aktor.v2.Aktor
import no.nav.sykmeldinger.application.ApplicationState
import no.nav.sykmeldinger.log
import no.nav.sykmeldinger.pdl.error.InactiveIdentException
import no.nav.sykmeldinger.pdl.error.PersonNotFoundInPdl
import org.apache.kafka.clients.consumer.KafkaConsumer

class PdlAktorConsumer(
    private val kafkaConsumer: KafkaConsumer<String, Aktor>,
    private val applicationState: ApplicationState,
    private val topic: String,
    private val identendringService: IdentendringService,
) {
    companion object {
        private const val DELAY_ON_ERROR_SECONDS = 60L
        private const val POLL_DURATION_SECONDS = 10L
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun startConsumer() {
        GlobalScope.launch(Dispatchers.IO) {
            while (applicationState.ready) {
                try {
                    runConsumer()
                } catch (ex: Exception) {
                    when (ex) {
                        is InactiveIdentException -> {
                            log.warn(
                                "New ident is inactive in PDL, unsubscribing and waiting $DELAY_ON_ERROR_SECONDS seconds for retry",
                                ex
                            )
                        }
                        is PersonNotFoundInPdl -> {
                            log.warn(
                                "Person not found in PDL, unsubscribing and waiting $DELAY_ON_ERROR_SECONDS seconds for retry",
                                ex
                            )
                        }
                        else -> {
                            log.error(
                                "Error running kafka consumer for pdl-aktor, unsubscribing and waiting $DELAY_ON_ERROR_SECONDS seconds for retry",
                                ex
                            )
                        }
                    }
                    kafkaConsumer.unsubscribe()
                    delay(DELAY_ON_ERROR_SECONDS.seconds)
                }
            }
        }
    }

    private suspend fun runConsumer() {
        kafkaConsumer.subscribe(listOf(topic))
        log.info("Starting consuming topic $topic")
        while (applicationState.ready) {
            kafkaConsumer.poll(Duration.ofSeconds(POLL_DURATION_SECONDS)).forEach {
                if (it.value() != null) {
                    identendringService.oppdaterIdent(it.value().identifikatorer)
                }
            }
        }
    }
}
