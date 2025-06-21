package no.nav.sykmeldinger.arbeidsforhold.client.organisasjon.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import no.nav.sykmeldinger.application.metrics.HTTP_CLIENT_HISTOGRAM
import no.nav.sykmeldinger.arbeidsforhold.client.organisasjon.model.Organisasjonsinfo
import no.nav.sykmeldinger.log

class OrganisasjonNotFoundException(message: String) : Exception()

data class OrganisjonsinfoErrorResponse(val melding: String)

class OrganisasjonsinfoClient(
    private val httpClient: HttpClient,
    private val url: String,
) {

    suspend fun getOrgnavn(orgNummer: String): String {
        val timer =
            HTTP_CLIENT_HISTOGRAM.labels("$url/api/v1/organisasjon/:orgNummer/noekkelinfo")
                .startTimer()
        try {
            val response = httpClient.get("$url/api/v1/organisasjon/$orgNummer/noekkelinfo")
            if (response.status == HttpStatusCode.NotFound) {
                val errorResponse = response.body<OrganisjonsinfoErrorResponse>()
                throw OrganisasjonNotFoundException(errorResponse.melding)
            }
            return response.body<Organisasjonsinfo>().navn.getNameAsString()
        } catch (e: Exception) {
            log.error("Noe gikk galt ved henting av organisasjon $orgNummer fra ereg", e)
            throw e
        } finally {
            timer.observeDuration()
        }
    }
}
