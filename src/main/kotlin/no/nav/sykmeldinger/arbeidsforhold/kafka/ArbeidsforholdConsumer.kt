package no.nav.sykmeldinger.arbeidsforhold.kafka

import java.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.nav.sykmeldinger.arbeidsforhold.ArbeidsforholdService
import no.nav.sykmeldinger.arbeidsforhold.kafka.model.ArbeidsforholdHendelse
import no.nav.sykmeldinger.arbeidsforhold.kafka.model.Endringstype
import no.nav.sykmeldinger.arbeidsforhold.kafka.model.Entitetsendring
import no.nav.sykmeldinger.arbeidsforhold.model.Arbeidsforhold
import no.nav.sykmeldinger.log
import no.nav.sykmeldinger.secureLog
import no.nav.sykmeldinger.sykmelding.db.SykmeldingDb
import org.apache.kafka.clients.consumer.KafkaConsumer

class ArbeidsforholdConsumer(
    private val kafkaConsumer: KafkaConsumer<String, ArbeidsforholdHendelse>,
    private val topic: String,
    private val sykmeldingDb: SykmeldingDb,
    private val arbeidsforholdService: ArbeidsforholdService,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) {

    private var job: Job? = null

    companion object {
        private const val DELAY_ON_ERROR_SECONDS = 60L
        private const val POLL_DURATION_SECONDS = 10L
    }

    fun startConsumer() {
        if (job == null || job!!.isCompleted) {
            job = scope.launch(Dispatchers.IO) { runConsumer() }
            log.info("ArbeidsforholdConsumer started")
        } else (log.info("ArbeidsforholdConsumer already running"))
    }

    fun stopConsumer() {
        if (job != null && job!!.isActive) {
            job?.cancel()
            job = null
            log.info("ArbeidsforholdConsumer stopped")
        } else {
            log.info("ArbeidsforholdConsumer already stopped")
        }
    }

    private suspend fun runConsumer() = coroutineScope {
        kafkaConsumer.subscribe(listOf(topic))
        log.info("Starting consuming topic $topic")
        while (isActive) {
            try {
                val hendelser = kafkaConsumer.poll(Duration.ofSeconds(POLL_DURATION_SECONDS))
                val newhendelserByFnr =
                    hendelser
                        .filter { it.value().endringstype != Endringstype.Sletting }
                        .filter { hasValidEndringstype(it.value()) }
                        .map { it.value().arbeidsforhold.arbeidstaker.getFnr() }
                        .distinct()

                newhendelserByFnr.chunked(10).forEach { updateArbeidsforholdFor(it) }

                val deleted =
                    hendelser
                        .filter { it.value().endringstype == Endringstype.Sletting }
                        .map { it.value().arbeidsforhold.navArbeidsforholdId }
                deleteArbeidsforhold(deleted)
                if (hendelser.count() > 0) {
                    log.info("Last hendelsesId ${hendelser.last().value().id}")
                }
            } catch (ex: Exception) {
                log.error(
                    "Error running kafka consumer for arbeidsforhold, unsubscribing and waiting $DELAY_ON_ERROR_SECONDS seconds for retry",
                    ex,
                )
                kafkaConsumer.unsubscribe()
                delay(DELAY_ON_ERROR_SECONDS.seconds)
                kafkaConsumer.subscribe(listOf(topic))
            }
        }
        log.info("Arbeidsforhold consumer coroutine not active")
        kafkaConsumer.unsubscribe()
    }

    private fun hasValidEndringstype(arbeidsforholdHendelse: ArbeidsforholdHendelse) =
        arbeidsforholdHendelse.entitetsendringer.any { endring ->
            endring == Entitetsendring.Ansettelsesdetaljer ||
                endring == Entitetsendring.Ansettelsesperiode
        }

    private suspend fun deleteArbeidsforhold(deleted: List<Int>) {
        arbeidsforholdService.deleteArbeidsforholdIds(deleted)
    }

    private suspend fun updateArbeidsforholdFor(newhendelserByFnr: List<String>) {
        withContext(Dispatchers.IO) {
            val jobs = newhendelserByFnr.map { async(Dispatchers.IO) { updateArbeidsforhold(it) } }
            jobs.awaitAll()
        }
    }

    private fun hasValidEndringstype(arbeidsforholdHendelse: ArbeidsforholdHendelse) =
        arbeidsforholdHendelse.entitetsendringer.any { endring ->
            endring == Entitetsendring.Ansettelsesdetaljer ||
                endring == Entitetsendring.Ansettelsesperiode
        }

    private suspend fun deleteArbeidsforhold(deleted: List<Int>) {
        arbeidsforholdService.deleteArbeidsforholdIds(deleted)
    }

    private suspend fun updateArbeidsforholdFor(newhendelserByFnr: List<String>) {
        withContext(Dispatchers.IO) {
            val jobs = newhendelserByFnr.map { async(Dispatchers.IO) { updateArbeidsforhold(it) } }
            jobs.awaitAll()
        }
    }

    suspend fun handleArbeidsforholdHendelse(arbeidsforholdHendelse: ArbeidsforholdHendelse) {
        log.info(
            "Mottatt arbeidsforhold-hendelse med id ${arbeidsforholdHendelse.id} og type ${arbeidsforholdHendelse.endringstype}",
        )
        val fnr = arbeidsforholdHendelse.arbeidsforhold.arbeidstaker.getFnr()
        val sykmeldt = sykmeldingDb.getSykmeldt(fnr)

        if (sykmeldt != null) {
            if (arbeidsforholdHendelse.endringstype == Endringstype.Sletting) {
                log.info(
                    "Sletter arbeidsforhold med id ${arbeidsforholdHendelse.arbeidsforhold.navArbeidsforholdId} hvis det finnes"
                )
                secureLog.info(
                    "Sletter arbeidsforhold, fnr: $fnr, arbeidsforholdId: ${arbeidsforholdHendelse.id}"
                )
                arbeidsforholdService.deleteArbeidsforhold(
                    arbeidsforholdHendelse.arbeidsforhold.navArbeidsforholdId,
                )
            } else {
                updateArbeidsforhold(fnr)
            }
        }
    }

    private suspend fun updateArbeidsforhold(fnr: String) {
        val arbeidsforhold = arbeidsforholdService.getArbeidsforhold(fnr)
        val arbeidsforholdFraDb = arbeidsforholdService.getArbeidsforholdFromDb(fnr)

        val slettesfraDb =
            getArbeidsforholdSomSkalSlettes(
                arbeidsforholdDb = arbeidsforholdFraDb,
                arbeidsforholdAareg = arbeidsforhold,
            )

        if (slettesfraDb.isNotEmpty()) {
            slettesfraDb.forEach {
                log.info(
                    "Sletter utdatert arbeidsforhold med id $it",
                )
                secureLog.info(
                    "Sletter fra arbeidsforhold, siden db og areg ulike, fnr: $fnr, arbeidsforholdId: $it",
                )
                arbeidsforholdService.deleteArbeidsforhold(it)
            }
        }
        arbeidsforhold.forEach { arbeidsforholdService.insertOrUpdate(it) }
        secureLog.info(
            "Opprettet eller oppdatert arbeidsforhold for $fnr for disse orgnr: ${arbeidsforhold.map { it.orgnummer }}",
        )
        log.info(
            "Opprettet eller oppdatert ${arbeidsforhold.size} arbeidsforhold med ider: ${arbeidsforhold.map { it.id }}",
        )
    }

    fun getArbeidsforholdSomSkalSlettes(
        arbeidsforholdAareg: List<Arbeidsforhold>,
        arbeidsforholdDb: List<Arbeidsforhold>
    ): List<Int> {
        if (
            arbeidsforholdDb.size == arbeidsforholdAareg.size &&
                arbeidsforholdDb.toHashSet() == arbeidsforholdAareg.toHashSet()
        ) {
            return emptyList()
        }

        val arbeidsforholdAaregMap: HashMap<Int, Arbeidsforhold> =
            HashMap(arbeidsforholdAareg.associateBy { it.id })
        val arbeidsforholdDbMap: HashMap<Int, Arbeidsforhold> =
            HashMap(arbeidsforholdDb.associateBy { it.id })

        return arbeidsforholdDbMap.filter { arbeidsforholdAaregMap[it.key] == null }.keys.toList()
    }
}
