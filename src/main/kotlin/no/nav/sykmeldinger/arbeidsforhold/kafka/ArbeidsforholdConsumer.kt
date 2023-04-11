package no.nav.sykmeldinger.arbeidsforhold.kafka

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.sykmeldinger.application.ApplicationState
import no.nav.sykmeldinger.arbeidsforhold.ArbeidsforholdService
import no.nav.sykmeldinger.arbeidsforhold.kafka.model.ArbeidsforholdHendelse
import no.nav.sykmeldinger.arbeidsforhold.kafka.model.Endringstype
import no.nav.sykmeldinger.arbeidsforhold.model.Arbeidsforhold
import no.nav.sykmeldinger.log
import no.nav.sykmeldinger.sykmelding.db.SykmeldingDb
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration
import kotlin.time.Duration.Companion.seconds

class ArbeidsforholdConsumer(
    private val kafkaConsumer: KafkaConsumer<String, ArbeidsforholdHendelse>,
    private val applicationState: ApplicationState,
    private val topic: String,
    private val sykmeldingDb: SykmeldingDb,
    private val arbeidsforholdService: ArbeidsforholdService,
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
                    log.error(
                        "Error running kafka consumer for arbeidsforhold, unsubscribing and waiting $DELAY_ON_ERROR_SECONDS seconds for retry",
                        ex,
                    )
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
                    handleArbeidsforholdHendelse(it.value())
                }
            }
        }
    }

    suspend fun handleArbeidsforholdHendelse(arbeidsforholdHendelse: ArbeidsforholdHendelse) {
        log.debug("Mottatt arbeidsforhold-hendelse med id ${arbeidsforholdHendelse.id} og type ${arbeidsforholdHendelse.endringstype}")
        val fnr = arbeidsforholdHendelse.arbeidsforhold.arbeidstaker.getFnr()
        val sykmeldt = sykmeldingDb.getSykmeldt(fnr)

        if (sykmeldt != null) {
            if (arbeidsforholdHendelse.endringstype == Endringstype.Sletting) {
                log.info("Sletter arbeidsforhold med id ${arbeidsforholdHendelse.arbeidsforhold.navArbeidsforholdId} hvis det finnes")
                arbeidsforholdService.deleteArbeidsforhold(arbeidsforholdHendelse.arbeidsforhold.navArbeidsforholdId)
            } else {
                val arbeidsforhold = arbeidsforholdService.getArbeidsforhold(fnr)
                val arbeidsforholdFraDb = arbeidsforholdService.getArbeidsforholdFromDb(fnr)

                val slettesfraDb = getArbeidsforholdSomSkalSlettes(arbeidsforholdDb = arbeidsforholdFraDb, arbeidsforholdAareg = arbeidsforhold)

                if (slettesfraDb.isNotEmpty()) {
                    slettesfraDb.forEach {
                        log.info("Sletter utdatert arbeidsforhold med id $it")
                        arbeidsforholdService.deleteArbeidsforhold(it)
                    }
                }
                arbeidsforhold.forEach {
                    arbeidsforholdService.insertOrUpdate(it)
                }
                log.info("Opprettet eller oppdatert ${arbeidsforhold.size} arbeidsforhold etter mottak av hendelse med id ${arbeidsforholdHendelse.id}")
            }
        }
    }

    fun getArbeidsforholdSomSkalSlettes(arbeidsforholdAareg: List<Arbeidsforhold>, arbeidsforholdDb: List<Arbeidsforhold>): List<Int> {
        if (arbeidsforholdDb.size == arbeidsforholdAareg.size && arbeidsforholdDb.toHashSet() == arbeidsforholdAareg.toHashSet()) {
            return emptyList()
        }

        val arbeidsforholdAaregMap: HashMap<Int, Arbeidsforhold> = HashMap(arbeidsforholdAareg.associateBy { it.id })
        val arbeidsforholdDbMap: HashMap<Int, Arbeidsforhold> = HashMap(arbeidsforholdDb.associateBy { it.id })

        return arbeidsforholdDbMap.filter { arbeidsforholdAaregMap[it.key] == null }.keys.toList()
    }
}
