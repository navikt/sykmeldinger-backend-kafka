package no.nav.sykmeldinger.pdl.service

import no.nav.sykmeldinger.azuread.AccessTokenClient
import no.nav.sykmeldinger.log
import no.nav.sykmeldinger.pdl.client.PdlClient
import no.nav.sykmeldinger.pdl.error.InactiveIdentException
import no.nav.sykmeldinger.pdl.error.PersonNotFoundInPdl
import no.nav.sykmeldinger.pdl.model.Navn
import no.nav.sykmeldinger.pdl.model.PdlPerson
import no.nav.sykmeldinger.sikkerlogg

class PdlPersonService(
    private val pdlClient: PdlClient,
    private val accessTokenClient: AccessTokenClient,
    private val pdlScope: String
) {

    suspend fun getPerson(fnr: String, callId: String): PdlPerson {
        val accessToken = accessTokenClient.getAccessToken(pdlScope)
        val pdlResponse = pdlClient.getPerson(fnr, accessToken)

        if (pdlResponse.errors != null) {
            pdlResponse.errors.forEach {
                log.error("PDL returnerte feilmelding: ${it.message}, ${it.extensions?.code}, $callId")
                it.extensions?.details?.let { details -> log.error("Type: ${details.type}, cause: ${details.cause}, policy: ${details.policy}, $callId") }
            }
        }
        if (pdlResponse.data.person == null) {
            sikkerlogg.info("Fant ikke person i PDL, fnr: $fnr")
            log.error("Fant ikke person i PDL {}", callId)
            throw PersonNotFoundInPdl("Fant ikke person i PDL")
        }
        if (pdlResponse.data.person.navn.isNullOrEmpty()) {
            sikkerlogg.info("Fant ikke navn på person i PDL, fnr: $fnr")
            log.error("Fant ikke navn på person i PDL {}", callId)
            throw PersonNotFoundInPdl("Fant ikke navn på person i PDL")
        }
        if (pdlResponse.data.hentIdenter == null || pdlResponse.data.hentIdenter.identer.isEmpty()) {
            sikkerlogg.info("Fant ikke person i PDL, fnr: $fnr")
            log.warn("Fant ikke person i PDL")
            throw PersonNotFoundInPdl("Fant ikke person i PDL")
        }
        if (pdlResponse.data.hentIdenter.fnr == null) {
            log.error("Mangler gyldig fnr for person i PDL")
            throw PersonNotFoundInPdl("Mangler gyldig fnr for person i PDL")
        }

        return PdlPerson(getNavn(pdlResponse.data.person.navn[0]), pdlResponse.data.hentIdenter.fnr)
    }

    suspend fun getNavnHvisIdentErAktiv(nyttFnr: String): Navn {
        val accessToken = accessTokenClient.getAccessToken(pdlScope)
        val pdlResponse = pdlClient.getPerson(nyttFnr, accessToken)

        if (pdlResponse.errors != null) {
            pdlResponse.errors.forEach {
                log.warn("PDL returnerte feilmelding: ${it.message}, ${it.extensions?.code}, ")
                it.extensions?.details?.let { details -> log.error("Type: ${details.type}, cause: ${details.cause}, policy: ${details.policy}") }
            }
        }
        if (pdlResponse.data.person == null || pdlResponse.data.person.navn.isNullOrEmpty()) {
            sikkerlogg.info("Fant ikke navn på person i PDL, nyttFnr: $nyttFnr")
            log.warn("Fant ikke navn på person i PDL")
            throw PersonNotFoundInPdl("Fant ikke navn på person i PDL")
        }
        if (pdlResponse.data.hentIdenter == null || pdlResponse.data.hentIdenter.identer.isEmpty()) {
            log.warn("Fant ikke person i PDL")
            throw PersonNotFoundInPdl("Fant ikke person i PDL")
        }
        // Spørring mot PDL er satt opp til å bare returnere aktive identer, og denne sjekken forutsetter dette
        if (pdlResponse.data.hentIdenter.fnr != nyttFnr || pdlResponse.data.hentIdenter.identer.any { it.ident == nyttFnr && it.historisk }) {
            throw InactiveIdentException("PDL svarer men ident er ikke aktiv")
        }
        return getNavn(pdlResponse.data.person.navn[0])
    }

    private fun getNavn(navn: no.nav.sykmeldinger.pdl.client.model.Navn): Navn {
        return Navn(fornavn = navn.fornavn, mellomnavn = navn.mellomnavn, etternavn = navn.etternavn)
    }
}
