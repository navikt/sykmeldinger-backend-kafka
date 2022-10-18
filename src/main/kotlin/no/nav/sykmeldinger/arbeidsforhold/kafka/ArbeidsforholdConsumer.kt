package no.nav.sykmeldinger.arbeidsforhold.kafka

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.sykmeldinger.application.ApplicationState
import no.nav.sykmeldinger.arbeidsforhold.ArbeidsforholdService
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.model.AaregArbeidsforhold
import no.nav.sykmeldinger.arbeidsforhold.client.organisasjon.client.OrganisasjonsinfoClient
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
    private val organisasjonsinfoClient: OrganisasjonsinfoClient
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
                        ex
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
        log.info("Mottatt arbeidsforhold-hendelse med id ${arbeidsforholdHendelse.id} og type ${arbeidsforholdHendelse.endringstype}")
        val fnr = arbeidsforholdHendelse.arbeidsforhold.arbeidstaker.getFnr()
        val sykmeldt = sykmeldingDb.getSykmeldt(fnr)

        if (sykmeldt != null) {
            if (arbeidsforholdHendelse.endringstype == Endringstype.Sletting) {
                log.info("Sletter arbeidsforhold med id ${arbeidsforholdHendelse.arbeidsforhold.navArbeidsforholdId} hvis det finnes")
                arbeidsforholdService.deleteArbeidsforhold(arbeidsforholdHendelse.arbeidsforhold.navArbeidsforholdId)
            } else if (arbeidsforholdService.arbeidsforholdErGyldig(arbeidsforholdHendelse.arbeidsforhold.ansettelsesperiode)) {
                arbeidsforholdService.insertOrUpdate(arbeidsforholdHendelse.arbeidsforhold.toArbeidsforhold(fnr))
                log.info("Opprettet eller oppdatert arbeidsforhold med id ${arbeidsforholdHendelse.arbeidsforhold.navArbeidsforholdId}")
            } else {
                log.info("Ingen relevante endringer for arbeidsforholdhendelse med id ${arbeidsforholdHendelse.id}")
            }
        }
    }

    private suspend fun AaregArbeidsforhold.toArbeidsforhold(fnr: String): Arbeidsforhold {
        val organisasjonsinfo = organisasjonsinfoClient.getOrganisasjonsnavn(arbeidssted.getOrgnummer())
        return Arbeidsforhold(
            id = navArbeidsforholdId,
            fnr = fnr,
            orgnummer = arbeidssted.getOrgnummer(),
            juridiskOrgnummer = opplysningspliktig.getJuridiskOrgnummer(),
            orgNavn = organisasjonsinfo.navn.getNameAsString(),
            fom = ansettelsesperiode.startdato,
            tom = ansettelsesperiode.sluttdato
        )
    }
}
