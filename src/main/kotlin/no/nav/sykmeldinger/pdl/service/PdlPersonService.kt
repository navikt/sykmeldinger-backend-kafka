package no.nav.sykmeldinger.pdl.service

import no.nav.sykmeldinger.azuread.AccessTokenClient
import no.nav.sykmeldinger.log
import no.nav.sykmeldinger.pdl.client.PdlClient
import no.nav.sykmeldinger.pdl.error.PersonNotFoundInPdl
import no.nav.sykmeldinger.pdl.model.Navn

class PdlPersonService(
    private val pdlClient: PdlClient,
    private val accessTokenClient: AccessTokenClient,
    private val pdlScope: String
) {

    suspend fun getNavn(fnr: String, callId: String): Navn {
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

        return getNavn(pdlResponse.data.person.navn[0])
    }

    private fun getNavn(navn: no.nav.sykmeldinger.pdl.client.model.Navn): Navn {
        return Navn(fornavn = navn.fornavn, mellomnavn = navn.mellomnavn, etternavn = navn.etternavn)
    }
}
