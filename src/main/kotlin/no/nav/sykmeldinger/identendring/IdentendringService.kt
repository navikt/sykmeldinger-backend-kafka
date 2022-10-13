package no.nav.sykmeldinger.identendring

import kotlinx.coroutines.DelicateCoroutinesApi
import no.nav.person.pdl.aktor.v2.Identifikator
import no.nav.person.pdl.aktor.v2.Type
import no.nav.sykmeldinger.application.metrics.NYTT_FNR_COUNTER
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.db.ArbeidsforholdDb
import no.nav.sykmeldinger.log
import no.nav.sykmeldinger.pdl.service.PdlPersonService

@DelicateCoroutinesApi
class IdentendringService(
    private val arbeidsforholdDb: ArbeidsforholdDb,
    private val pdlService: PdlPersonService
) {
    suspend fun oppdaterIdent(identListe: List<Identifikator>) {
        if (harEndretFnr(identListe)) {
            val nyttFnr = identListe.find { it.type == Type.FOLKEREGISTERIDENT && it.gjeldende }?.idnummer
                ?: throw IllegalStateException("Mangler gyldig fnr!")
            val tidligereFnr = identListe.filter { it.type == Type.FOLKEREGISTERIDENT && !it.gjeldende }

            // oppdater arbeidsforhold, sykmeldinger og evt navn (nærmeste leder oppdateres via kafka)
            val arbeidsforhold = tidligereFnr.flatMap { arbeidsforholdDb.getArbeidsforhold(it.idnummer) }

            if (arbeidsforhold.isNotEmpty()) {
                pdlService.erIdentAktiv(nyttFnr)
            }
            arbeidsforhold.forEach {
                arbeidsforholdDb.updateFnr(nyttFnr = nyttFnr, id = it.id)
            }
            log.info("Har oppdatert fnr for ${arbeidsforhold.size} arbeidsforhold")

            if (arbeidsforhold.isNotEmpty()) {
                NYTT_FNR_COUNTER.inc()
            }
        }
    }

    private fun harEndretFnr(identListe: List<Identifikator>): Boolean {
        if (identListe.filter { it.type == Type.FOLKEREGISTERIDENT }.size < 2) {
            log.debug("Identendring inneholder ingen endring i fnr")
            return false
        }
        return true
    }
}
