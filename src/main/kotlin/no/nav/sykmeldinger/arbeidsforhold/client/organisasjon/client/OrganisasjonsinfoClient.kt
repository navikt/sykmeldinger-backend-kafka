package no.nav.sykmeldinger.arbeidsforhold.client.organisasjon.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import no.nav.sykmeldinger.application.metrics.HTTP_CLIENT_HISTOGRAM
import no.nav.sykmeldinger.arbeidsforhold.client.organisasjon.model.Organisasjonsinfo
import no.nav.sykmeldinger.log

class OrganisasjonsinfoClient(
    private val httpClient: HttpClient,
    private val url: String,
) {
    suspend fun getOrganisasjonsnavn(orgNummer: String): Organisasjonsinfo {
        val timer =
            HTTP_CLIENT_HISTOGRAM.labels("$url/api/v1/organisasjon/:orgNummer/noekkelinfo")
                .startTimer()
        try {
            return httpClient.get("$url/api/v1/organisasjon/$orgNummer/noekkelinfo").body()
        } catch (e: Exception) {
            log.error("Noe gikk galt ved henting av organisasjon $orgNummer fra ereg")
            throw e
        } finally {
            timer.observeDuration()
        }
    }
}
