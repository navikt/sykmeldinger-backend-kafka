package no.nav.sykmeldinger.identendring

import no.nav.person.pdl.aktor.v2.Identifikator
import no.nav.person.pdl.aktor.v2.Type
import no.nav.sykmeldinger.application.metrics.NYTT_FNR_COUNTER
import no.nav.sykmeldinger.arbeidsforhold.db.ArbeidsforholdDb
import no.nav.sykmeldinger.log
import no.nav.sykmeldinger.pdl.service.PdlPersonService
import no.nav.sykmeldinger.sykmelding.db.SykmeldingDb
import no.nav.sykmeldinger.sykmelding.model.Sykmeldt

class IdentendringService(
    private val arbeidsforholdDb: ArbeidsforholdDb,
    private val sykmeldingDb: SykmeldingDb,
    private val pdlService: PdlPersonService
) {
    suspend fun oppdaterIdent(identListe: List<Identifikator>) {
        if (harEndretFnr(identListe)) {
            val nyttFnr = identListe.find { it.type == Type.FOLKEREGISTERIDENT && it.gjeldende }?.idnummer
                ?: throw IllegalStateException("Mangler gyldig fnr!")
            val tidligereFnr = identListe.filter { it.type == Type.FOLKEREGISTERIDENT && !it.gjeldende }

            val arbeidsforhold = tidligereFnr.flatMap { arbeidsforholdDb.getArbeidsforhold(it.idnummer) }
            val sykmeldinger = tidligereFnr.flatMap { sykmeldingDb.getSykmeldingIds(it.idnummer) }
            val sykmeldte = tidligereFnr.mapNotNull { sykmeldingDb.getSykmeldt(it.idnummer) }

            if (arbeidsforhold.isNotEmpty() || sykmeldinger.isNotEmpty() || sykmeldte.isNotEmpty()) {
                val navn = pdlService.getNavnHvisIdentErAktiv(nyttFnr)

                arbeidsforhold.forEach {
                    arbeidsforholdDb.updateFnr(nyttFnr = nyttFnr, id = it.id)
                }
                log.info("Har oppdatert fnr for ${arbeidsforhold.size} arbeidsforhold")

                sykmeldinger.forEach {
                    sykmeldingDb.updateFnr(nyttFnr = nyttFnr, sykmeldingId = it)
                }
                log.info("Har oppdatert fnr for ${sykmeldinger.size} sykmeldinger")

                sykmeldingDb.saveOrUpdateSykmeldt(
                    Sykmeldt(
                        fnr = nyttFnr,
                        fornavn = navn.fornavn,
                        mellomnavn = navn.mellomnavn,
                        etternavn = navn.etternavn
                    )
                )
                sykmeldte.forEach {
                    sykmeldingDb.deleteSykmeldt(it.fnr)
                }
                log.info("Har slettet ${sykmeldte.size} sykmeldte")

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
