package no.nav.sykmeldinger.pdl.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import no.nav.sykmeldinger.application.metrics.HTTP_CLIENT_HISTOGRAM
import no.nav.sykmeldinger.log
import no.nav.sykmeldinger.pdl.client.model.GetPersonRequest
import no.nav.sykmeldinger.pdl.client.model.GetPersonResponse
import no.nav.sykmeldinger.pdl.client.model.GetPersonVariables

class PdlClient(
    private val httpClient: HttpClient,
    private val basePath: String,
    private val graphQlQuery: String,
) {
    private val temaHeader = "TEMA"
    private val tema = "SYM"

    suspend fun getPerson(ident: String, token: String): GetPersonResponse {
        val getPersonRequest =
            GetPersonRequest(query = graphQlQuery, variables = GetPersonVariables(ident = ident))
        val timer = HTTP_CLIENT_HISTOGRAM.labels(basePath).startTimer()
        try {
            return httpClient
                .post(basePath) {
                    setBody(getPersonRequest)
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header(temaHeader, tema)
                    header(HttpHeaders.ContentType, "application/json")
                }
                .body()
        } catch (e: Exception) {
            log.error("Noe gikk galt ved kall til PDL", e)
            throw e
        } finally {
            timer.observeDuration()
        }
    }
}
