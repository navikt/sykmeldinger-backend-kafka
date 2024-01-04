package no.nav.sykmeldinger.arbeidsforhold

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.common.runBlocking
import io.kotest.core.spec.style.FunSpec
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.jackson.jackson
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import no.nav.sykmeldinger.Environment
import no.nav.sykmeldinger.application.db.Database
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.client.ArbeidsforholdClient
import no.nav.sykmeldinger.arbeidsforhold.client.organisasjon.client.OrganisasjonsinfoClient
import no.nav.sykmeldinger.arbeidsforhold.db.ArbeidsforholdDb
import no.nav.sykmeldinger.azuread.AccessTokenClient

class ArbeidsforholdServiceIntegrationTest :
    FunSpec({
        val mockAccessToken = mockk<AccessTokenClient>()
        coEvery { mockAccessToken.getAccessToken(any()) } returns "<token>"
        val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
            install(ContentNegotiation) {
                jackson {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                }
            }
        }
        val httpClient = HttpClient(Apache, config)
        val client =
            ArbeidsforholdClient(
                httpClient,
                "https://aareg-services.dev.intern.nav.no",
                mockAccessToken,
                "scope"
            )
        val env =
            mockk<Environment>().apply {
                every { dbHost } returns "127.0.0.1"
                every { dbName } returns "sykmeldinger"
                every { databasePassword } returns "<password>"
                every { databaseUsername } returns "sykmeldinger-db-instance"
                every { dbPort } returns "5439"
                every { cloudSqlInstance } returns
                    "teamsykmelding-dev-1d34:europe-north1:sykmeldinger-db-instance"
            }
        val arbeidsforholdDb = ArbeidsforholdDb(Database(env))
        val organisasjonsinfoClient =
            OrganisasjonsinfoClient(httpClient, "https://ereg-services.dev.intern.nav.no")
        val arbeidsforholdService =
            ArbeidsforholdService(client, organisasjonsinfoClient, arbeidsforholdDb)
        runBlocking {
            val arbeidsforhold = arbeidsforholdService.getArbeidsforhold("")
            arbeidsforholdService.insertOrUpdate(arbeidsforhold.first())
        }
    })
