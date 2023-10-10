package no.nav.sykmeldinger

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.network.sockets.SocketTimeoutException
import io.ktor.serialization.jackson.jackson
import io.prometheus.client.hotspot.DefaultExports
import kotlinx.coroutines.DelicateCoroutinesApi
import no.nav.person.pdl.aktor.v2.Aktor
import no.nav.person.pdl.leesah.Personhendelse
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import no.nav.sykmeldinger.application.ApplicationServer
import no.nav.sykmeldinger.application.ApplicationState
import no.nav.sykmeldinger.application.createApplicationEngine
import no.nav.sykmeldinger.application.db.Database
import no.nav.sykmeldinger.application.exception.ServiceUnavailableException
import no.nav.sykmeldinger.application.leaderelection.LeaderElection
import no.nav.sykmeldinger.arbeidsforhold.ArbeidsforholdService
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.client.ArbeidsforholdClient
import no.nav.sykmeldinger.arbeidsforhold.client.organisasjon.client.OrganisasjonsinfoClient
import no.nav.sykmeldinger.arbeidsforhold.db.ArbeidsforholdDb
import no.nav.sykmeldinger.arbeidsforhold.delete.DeleteArbeidsforholdService
import no.nav.sykmeldinger.arbeidsforhold.kafka.ArbeidsforholdConsumer
import no.nav.sykmeldinger.arbeidsforhold.kafka.model.ArbeidsforholdHendelse
import no.nav.sykmeldinger.azuread.AccessTokenClient
import no.nav.sykmeldinger.behandlingsutfall.db.BehandlingsutfallDB
import no.nav.sykmeldinger.behandlingsutfall.kafka.BehandlingsutfallConsumer
import no.nav.sykmeldinger.identendring.IdentendringService
import no.nav.sykmeldinger.identendring.PdlAktorConsumer
import no.nav.sykmeldinger.narmesteleder.NarmesteLederService
import no.nav.sykmeldinger.narmesteleder.db.NarmestelederDb
import no.nav.sykmeldinger.narmesteleder.kafka.NarmesteLederConsumer
import no.nav.sykmeldinger.narmesteleder.kafka.NarmestelederLeesahKafkaMessage
import no.nav.sykmeldinger.navnendring.NavnendringConsumer
import no.nav.sykmeldinger.pdl.client.PdlClient
import no.nav.sykmeldinger.pdl.service.PdlPersonService
import no.nav.sykmeldinger.status.db.SykmeldingStatusDB
import no.nav.sykmeldinger.status.kafka.SykmeldingStatusConsumer
import no.nav.sykmeldinger.sykmelding.SykmeldingService
import no.nav.sykmeldinger.sykmelding.db.SykmeldingDb
import no.nav.sykmeldinger.sykmelding.kafka.SykmeldingConsumer
import no.nav.sykmeldinger.util.kafka.JacksonKafkaDeserializer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val log: Logger = LoggerFactory.getLogger("no.nav.sykmeldinger.sykmeldinger-backend-kafka")
val secureLog = LoggerFactory.getLogger("securelog")

val objectMapper: ObjectMapper =
    jacksonObjectMapper().apply {
        registerModule(JavaTimeModule())
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
    }

@OptIn(DelicateCoroutinesApi::class)
fun main() {
    val env = Environment()
    DefaultExports.initialize()
    val applicationState = ApplicationState()
    val applicationEngine =
        createApplicationEngine(
            env,
            applicationState,
        )
    val applicationServer = ApplicationServer(applicationEngine, applicationState)
    val database = Database(env)

    val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        install(HttpRequestRetry) {
            constantDelay(100, 0, false)
            retryOnExceptionIf(3) { request, throwable ->
                log.warn("Caught exception ${throwable.message}, for url ${request.url}")
                true
            }
            retryIf(maxRetries) { request, response ->
                if (response.status.value.let { it in 500..599 }) {
                    log.warn(
                        "Retrying for statuscode ${response.status.value}, for url ${request.url}"
                    )
                    true
                } else {
                    false
                }
            }
        }
        install(HttpTimeout) {
            socketTimeoutMillis = 40_000
            connectTimeoutMillis = 40_000
            requestTimeoutMillis = 40_000
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
                    is SocketTimeoutException ->
                        throw ServiceUnavailableException(exception.message)
                }
            }
        }
        expectSuccess = true
    }
    val httpClient = HttpClient(Apache, config)

    val accessTokenClient =
        AccessTokenClient(env.aadAccessTokenUrl, env.clientId, env.clientSecret, httpClient)
    val pdlClient =
        PdlClient(
            httpClient,
            env.pdlGraphqlPath,
            PdlClient::class
                .java
                .getResource("/graphql/getPerson.graphql")!!
                .readText()
                .replace(Regex("[\n\t]"), ""),
        )
    val pdlPersonService = PdlPersonService(pdlClient, accessTokenClient, env.pdlScope)

    val kafkaConsumer = getSykmeldingStatusKafkaConsumer()
    val sykmeldingStatusDB = SykmeldingStatusDB(database)
    val sykmeldingStatusConsumer =
        SykmeldingStatusConsumer(env, kafkaConsumer, sykmeldingStatusDB, applicationState)
    sykmeldingStatusConsumer.startConsumer()

    val narmesteLederKafkaConsumer = getNarmesteLederKafkaConsumer()
    val narmestelederDb = NarmestelederDb(database)
    val narmesteLederService = NarmesteLederService(pdlPersonService, narmestelederDb, env.cluster)
    val narmesteLederConsumer =
        NarmesteLederConsumer(
            env,
            narmesteLederKafkaConsumer,
            narmesteLederService,
            applicationState
        )
    val behandlingsutfallDB = BehandlingsutfallDB(database)
    val behandlingsutfallConsumer =
        BehandlingsutfallConsumer(getKafkaConsumer(), applicationState, env, behandlingsutfallDB)
    behandlingsutfallConsumer.startConsumer()
    narmesteLederConsumer.startConsumer()

    val navnendringConsumer =
        NavnendringConsumer(
            env.navnendringTopic,
            getNavnendringerConsumer(env),
            applicationState,
            narmestelederDb,
            pdlPersonService
        )
    navnendringConsumer.startConsumer()

    val arbeidsforholdClient =
        ArbeidsforholdClient(httpClient, env.aaregUrl, accessTokenClient, env.aaregScope)
    val arbeidsforholdDb = ArbeidsforholdDb(database)
    val organisasjonsinfoClient = OrganisasjonsinfoClient(httpClient, env.eregUrl)
    val arbeidsforholdService =
        ArbeidsforholdService(arbeidsforholdClient, organisasjonsinfoClient, arbeidsforholdDb)

    val sykmeldingDb = SykmeldingDb(database)
    val sykmeldingService = SykmeldingService(sykmeldingDb)
    val sykmeldingConsumer =
        SykmeldingConsumer(
            getKafkaConsumer(),
            applicationState,
            pdlPersonService,
            arbeidsforholdService,
            sykmeldingService,
            env.cluster
        )
    sykmeldingConsumer.startConsumer()

    val identendringService = IdentendringService(arbeidsforholdDb, sykmeldingDb, pdlPersonService)
    val pdlAktorConsumer =
        PdlAktorConsumer(
            getIdentendringConsumer(env),
            applicationState,
            env.aktorV2Topic,
            identendringService
        )
    pdlAktorConsumer.startConsumer()

    val arbeidsforholdConsumer =
        ArbeidsforholdConsumer(
            getArbeidsforholdKafkaConsumer(),
            applicationState,
            env.arbeidsforholdTopic,
            sykmeldingDb,
            arbeidsforholdService
        )
    arbeidsforholdConsumer.startConsumer()

    val leaderElection = LeaderElection(httpClient, env.electorPath)
    val deleteArbeidsforholdService =
        DeleteArbeidsforholdService(arbeidsforholdDb, leaderElection, applicationState)
    deleteArbeidsforholdService.start()

    applicationServer.start()
}

private fun getKafkaConsumer(): KafkaConsumer<String, String> {
    val kafkaConsumer =
        KafkaConsumer(
            KafkaUtils.getAivenKafkaConfig("sykmeldinger-backend-kafka-consumer")
                .also {
                    it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none"
                    it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = 100
                }
                .toConsumerConfig("sykmeldinger-backend-kafka-consumer", StringDeserializer::class),
            StringDeserializer(),
            StringDeserializer(),
        )
    return kafkaConsumer
}

private fun getSykmeldingStatusKafkaConsumer():
    KafkaConsumer<String, SykmeldingStatusKafkaMessageDTO> {
    val kafkaConsumer =
        KafkaConsumer(
            KafkaUtils.getAivenKafkaConfig("sykmeldinger-status-consumer")
                .also {
                    it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none"
                    it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = 10
                }
                .toConsumerConfig(
                    "sykmeldinger-backend-kafka-consumer",
                    JacksonKafkaDeserializer::class
                ),
            StringDeserializer(),
            JacksonKafkaDeserializer(SykmeldingStatusKafkaMessageDTO::class),
        )
    return kafkaConsumer
}

private fun getNarmesteLederKafkaConsumer():
    KafkaConsumer<String, NarmestelederLeesahKafkaMessage> {
    val kafkaConsumer =
        KafkaConsumer(
            KafkaUtils.getAivenKafkaConfig("narmeste-leder-consumer")
                .also {
                    it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none"
                    it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = 10
                }
                .toConsumerConfig(
                    "sykmeldinger-backend-kafka-consumer",
                    JacksonKafkaDeserializer::class
                ),
            StringDeserializer(),
            JacksonKafkaDeserializer(NarmestelederLeesahKafkaMessage::class),
        )
    return kafkaConsumer
}

private fun getNavnendringerConsumer(
    environment: Environment
): KafkaConsumer<String, Personhendelse> {
    val consumerProperties =
        KafkaUtils.getAivenKafkaConfig("navne-endring-consumer")
            .apply {
                setProperty(
                    KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG,
                    environment.schemaRegistryUrl
                )
                setProperty(
                    KafkaAvroSerializerConfig.USER_INFO_CONFIG,
                    "${environment.kafkaSchemaRegistryUsername}:${environment.kafkaSchemaRegistryPassword}"
                )
                setProperty(KafkaAvroSerializerConfig.BASIC_AUTH_CREDENTIALS_SOURCE, "USER_INFO")
            }
            .toConsumerConfig(
                "sykmeldinger-backend-kafka-consumer",
                valueDeserializer = KafkaAvroDeserializer::class,
            )
            .also {
                it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none"
                it["specific.avro.reader"] = true
            }
    return KafkaConsumer<String, Personhendelse>(consumerProperties)
}

private fun getIdentendringConsumer(environment: Environment): KafkaConsumer<String, Aktor> {
    val consumerProperties =
        KafkaUtils.getAivenKafkaConfig("identendring-consumer")
            .apply {
                setProperty(
                    KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG,
                    environment.schemaRegistryUrl
                )
                setProperty(
                    KafkaAvroSerializerConfig.USER_INFO_CONFIG,
                    "${environment.kafkaSchemaRegistryUsername}:${environment.kafkaSchemaRegistryPassword}"
                )
                setProperty(KafkaAvroSerializerConfig.BASIC_AUTH_CREDENTIALS_SOURCE, "USER_INFO")
            }
            .toConsumerConfig(
                "sykmeldinger-backend-kafka-consumer",
                valueDeserializer = KafkaAvroDeserializer::class,
            )
            .also {
                it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none"
                it["specific.avro.reader"] = true
            }
    return KafkaConsumer<String, Aktor>(consumerProperties)
}

private fun getArbeidsforholdKafkaConsumer(): KafkaConsumer<String, ArbeidsforholdHendelse> {
    val kafkaConsumer =
        KafkaConsumer(
            KafkaUtils.getAivenKafkaConfig("arbeidsforhold-consumer")
                .also {
                    it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none"
                    it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = 10
                }
                .toConsumerConfig(
                    "sykmeldinger-backend-kafka-consumer",
                    JacksonKafkaDeserializer::class
                ),
            StringDeserializer(),
            JacksonKafkaDeserializer(ArbeidsforholdHendelse::class),
        )
    return kafkaConsumer
}
