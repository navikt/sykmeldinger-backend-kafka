package no.nav.sykmeldinger.pdl.service

import no.nav.sykmeldinger.azuread.AccessTokenClient
import no.nav.sykmeldinger.log
import no.nav.sykmeldinger.pdl.client.PdlClient
import no.nav.sykmeldinger.pdl.error.InactiveIdentException
import no.nav.sykmeldinger.pdl.error.PersonNotFoundInPdl
import no.nav.sykmeldinger.pdl.model.Navn
import no.nav.sykmeldinger.pdl.model.PdlPerson

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
            log.error("Fant ikke person i PDL {}", callId)
            throw PersonNotFoundInPdl("Fant ikke person i PDL")
        }
        if (pdlResponse.data.person.navn.isNullOrEmpty()) {
            log.error("Fant ikke navn på person i PDL {}", callId)
            throw PersonNotFoundInPdl("Fant ikke navn på person i PDL")
        }
        if (pdlResponse.data.hentIdenter == null || pdlResponse.data.hentIdenter.identer.isEmpty()) {
            log.warn("Fant ikke person i PDL")
            throw PersonNotFoundInPdl("Fant ikke person i PDL")
        }
        if (pdlResponse.data.hentIdenter.fnr == null) {
            log.error("Mangler gyldig fnr for person i PDL")
            throw PersonNotFoundInPdl("Mangler gyldig fnr for person i PDL")
        }

        return PdlPerson(getNavn(pdlResponse.data.person.navn[0]), pdlResponse.data.hentIdenter.fnr)
    }

    suspend fun erIdentAktiv(nyttFnr: String): Boolean {
        val accessToken = accessTokenClient.getAccessToken(pdlScope)
        val pdlResponse = pdlClient.getPerson(nyttFnr, accessToken)

        if (pdlResponse.errors != null) {
            pdlResponse.errors.forEach {
                log.error("PDL returnerte feilmelding: ${it.message}, ${it.extensions?.code}, ")
                it.extensions?.details?.let { details -> log.error("Type: ${details.type}, cause: ${details.cause}, policy: ${details.policy}") }
            }
        }
        if (pdlResponse.data.hentIdenter == null || pdlResponse.data.hentIdenter.identer.isEmpty()) {
            log.warn("Fant ikke person i PDL")
            throw PersonNotFoundInPdl("Fant ikke person i PDL")
        }
        // Spørring mot PDL er satt opp til å bare returnere aktive identer, og denne sjekken forutsetter dette
        if (pdlResponse.data.hentIdenter.fnr != nyttFnr || pdlResponse.data.hentIdenter.identer.any { it.ident == nyttFnr && it.historisk }) {
            throw InactiveIdentException("PDL svarer men ident er ikke aktiv")
        }
        return true
    }

    private fun getNavn(navn: no.nav.sykmeldinger.pdl.client.model.Navn): Navn {
        return Navn(fornavn = navn.fornavn, mellomnavn = navn.mellomnavn, etternavn = navn.etternavn)
    }
}
