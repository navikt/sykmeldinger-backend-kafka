package no.nav.sykmeldinger

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.network.sockets.SocketTimeoutException
import io.ktor.serialization.jackson.jackson
import io.prometheus.client.hotspot.DefaultExports
import no.nav.person.pdl.leesah.Personhendelse
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import no.nav.sykmeldinger.application.ApplicationServer
import no.nav.sykmeldinger.application.ApplicationState
import no.nav.sykmeldinger.application.createApplicationEngine
import no.nav.sykmeldinger.application.db.Database
import no.nav.sykmeldinger.application.exception.ServiceUnavailableException
import no.nav.sykmeldinger.arbeidsforhold.ArbeidsforholdService
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.client.ArbeidsforholdClient
import no.nav.sykmeldinger.arbeidsforhold.client.organisasjon.client.OrganisasjonsinfoClient
import no.nav.sykmeldinger.azuread.AccessTokenClient
import no.nav.sykmeldinger.narmesteleder.NarmesteLederService
import no.nav.sykmeldinger.narmesteleder.db.NarmestelederDb
import no.nav.sykmeldinger.narmesteleder.kafka.NarmesteLederConsumer
import no.nav.sykmeldinger.narmesteleder.kafka.NarmestelederLeesahKafkaMessage
import no.nav.sykmeldinger.navnendring.NavnendringConsumer
import no.nav.sykmeldinger.pdl.client.PdlClient
import no.nav.sykmeldinger.pdl.service.PdlPersonService
import no.nav.sykmeldinger.status.kafka.SykmeldingStatusConsumer
import no.nav.sykmeldinger.util.kafka.JacksonKafkaDeserializer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val log: Logger = LoggerFactory.getLogger("no.nav.sykmeldinger.sykmeldinger-backend-kafka")

fun main() {
    val env = Environment()
    DefaultExports.initialize()
    val applicationState = ApplicationState()
    val applicationEngine = createApplicationEngine(
        env,
        applicationState
    )
    val applicationServer = ApplicationServer(applicationEngine, applicationState)
    val database = Database(env)

    val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        install(HttpRequestRetry) {
            constantDelay(100, 0, false)
            retryOnExceptionIf(3) { _, throwable ->
                log.warn("Caught exception ${throwable.message}")
                true
            }
            retryIf(maxRetries) { _, response ->
                if (response.status.value.let { it in 500..599 }) {
                    log.warn("Retrying for statuscode ${response.status.value}")
                    true
                } else {
                    false
                }
            }
        }
        install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        HttpResponseValidator {
            handleResponseExceptionWithRequest { exception, _ ->
                when (exception) {
                    is SocketTimeoutException -> throw ServiceUnavailableException(exception.message)
                }
            }
        }
        expectSuccess = true
    }
    val httpClient = HttpClient(Apache, config)

    val accessTokenClient = AccessTokenClient(env.aadAccessTokenUrl, env.clientId, env.clientSecret, httpClient)
    val pdlClient = PdlClient(
        httpClient,
        env.pdlGraphqlPath,
        PdlClient::class.java.getResource("/graphql/getPerson.graphql").readText().replace(Regex("[\n\t]"), "")
    )
    val pdlPersonService = PdlPersonService(pdlClient, accessTokenClient, env.pdlScope)

    val kafkaConsumer = getSykmeldingStatusKafkaConsumer()
    val sykmeldingStatusConsumer = SykmeldingStatusConsumer(env, kafkaConsumer, database, applicationState)
    sykmeldingStatusConsumer.startConsumer()

    val narmesteLederKafkaConsumer = getNarmesteLederKafkaConsumer()
    val narmestelederDb = NarmestelederDb(database)
    val narmesteLederService = NarmesteLederService(pdlPersonService, narmestelederDb, env.cluster)
    val narmesteLederConsumer = NarmesteLederConsumer(env, narmesteLederKafkaConsumer, narmesteLederService, applicationState)
    narmesteLederConsumer.startConsumer()

    val navnendringConsumer = NavnendringConsumer(env.navnendringTopic, getNavnendringerConsumer(env), applicationState, narmestelederDb, pdlPersonService)
    navnendringConsumer.startConsumer()

    val arbeidsforholdClient = ArbeidsforholdClient(httpClient, env.aaregUrl, accessTokenClient, env.aaregScope)
    val organisasjonsinfoClient = OrganisasjonsinfoClient(httpClient, env.eregUrl)
    val arbeidsforholdService = ArbeidsforholdService(arbeidsforholdClient, organisasjonsinfoClient)

    applicationServer.start()
}

private fun getSykmeldingStatusKafkaConsumer(): KafkaConsumer<String, SykmeldingStatusKafkaMessageDTO> {
    val kafkaConsumer = KafkaConsumer(
        KafkaUtils.getAivenKafkaConfig().also {
            it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
            it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = 1000
        }.toConsumerConfig("sykmeldinger-backend-kafka-consumer", JacksonKafkaDeserializer::class),
        StringDeserializer(),
        JacksonKafkaDeserializer(SykmeldingStatusKafkaMessageDTO::class)
    )
    return kafkaConsumer
}

private fun getNarmesteLederKafkaConsumer(): KafkaConsumer<String, NarmestelederLeesahKafkaMessage> {
    val kafkaConsumer = KafkaConsumer(
        KafkaUtils.getAivenKafkaConfig().also {
            it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none"
            it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = 100
        }.toConsumerConfig("sykmeldinger-backend-kafka-consumer", JacksonKafkaDeserializer::class),
        StringDeserializer(),
        JacksonKafkaDeserializer(NarmestelederLeesahKafkaMessage::class)
    )
    return kafkaConsumer
}

private fun getNavnendringerConsumer(environment: Environment): KafkaConsumer<String, Personhendelse> {
    val consumerProperties = KafkaUtils.getAivenKafkaConfig().apply {
        setProperty(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, environment.schemaRegistryUrl)
        setProperty(KafkaAvroSerializerConfig.USER_INFO_CONFIG, "${environment.kafkaSchemaRegistryUsername}:${environment.kafkaSchemaRegistryPassword}")
        setProperty(KafkaAvroSerializerConfig.BASIC_AUTH_CREDENTIALS_SOURCE, "USER_INFO")
    }.toConsumerConfig(
        "sykmeldinger-backend-kafka-consumer",
        valueDeserializer = KafkaAvroDeserializer::class
    ).also {
        it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "latest"
        it["specific.avro.reader"] = true
    }

    return KafkaConsumer<String, Personhendelse>(consumerProperties)
}
