package no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import no.nav.sykmeldinger.application.metrics.HTTP_CLIENT_HISTOGRAM
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.model.AaregArbeidsforhold
import no.nav.sykmeldinger.azuread.AccessTokenClient
import no.nav.sykmeldinger.log

class ArbeidsforholdClient(
    private val httpClient: HttpClient,
    private val url: String,
    private val accessTokenClient: AccessTokenClient,
    private val scope: String
) {

    private val arbeidsforholdPath = "$url/api/v2/arbeidstaker/arbeidsforhold"
    private val navPersonident = "Nav-Personident"

    suspend fun getArbeidsforhold(fnr: String): List<AaregArbeidsforhold> {
        val token = accessTokenClient.getAccessToken(scope)
        val timer = HTTP_CLIENT_HISTOGRAM.labels(arbeidsforholdPath).startTimer()
        try {
            return httpClient.get(
                "$arbeidsforholdPath?" +
                    "sporingsinformasjon=false&" +
                    "arbeidsforholdstatus=AKTIV,FREMTIDIG,AVSLUTTET"
            ) {
                header(navPersonident, fnr)
                header(HttpHeaders.Authorization, "Bearer $token")
            }.body()
        } catch (e: Exception) {
            log.error("Noe gikk galt ved henting av arbeidsforhold", e)
            throw e
        } finally {
            timer.observeDuration()
        }
    }
}
