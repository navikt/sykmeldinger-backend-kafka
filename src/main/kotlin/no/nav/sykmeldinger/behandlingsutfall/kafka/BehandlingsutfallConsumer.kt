package no.nav.sykmeldinger.behandlingsutfall.kafka

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.nav.syfo.model.ValidationResult
import no.nav.sykmeldinger.Environment
import no.nav.sykmeldinger.application.ApplicationState
import no.nav.sykmeldinger.behandlingsutfall.Behandlingsutfall
import no.nav.sykmeldinger.behandlingsutfall.db.BehandlingsutfallDB
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

private val objectMapper: ObjectMapper = jacksonObjectMapper().apply {
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
}

class BehandlingsutfallConsumer(
    private val kafkaConsumer: KafkaConsumer<String, String>,
    private val applicationState: ApplicationState,
    private val environment: Environment,
    private val behandlingsutfallDb: BehandlingsutfallDB
) {
    companion object {
        private val log = LoggerFactory.getLogger(BehandlingsutfallConsumer::class.java)
    }

    private var totalRecords = 0
    private var lastDate = OffsetDateTime.MIN
    private var behandlingsutfallTopics = 0
    @OptIn(DelicateCoroutinesApi::class)
    fun startConsumer() {
        GlobalScope.launch(Dispatchers.IO) {
            GlobalScope.launch(Dispatchers.IO) {
                while (applicationState.ready) {
                    no.nav.sykmeldinger.log.info("total records: $totalRecords, behandlingsutfall: $behandlingsutfallTopics recods  last record was at $lastDate")
                    delay(10000)
                }
            }
            while (applicationState.ready) {
                try {
                    kafkaConsumer.subscribe(listOf(environment.historiskTopic))
                    consume()
                } catch (ex: Exception) {
                    log.error("error running behandlingsutfall-consumer", ex)
                } finally {
                    kafkaConsumer.unsubscribe()
                    log.info("Unsubscribed from topic ${environment.historiskTopic} and waiting for 10 seconds before trying again")
                    delay(10_000)
                }
            }
        }
    }

    private suspend fun consume() = withContext(Dispatchers.IO) {
        while (applicationState.ready) {
            val consumerRecords = kafkaConsumer.poll(Duration.ofSeconds(1))
            if (!consumerRecords.isEmpty) {
                totalRecords += consumerRecords.count()
                lastDate = OffsetDateTime.ofInstant(
                    Instant.ofEpochMilli(consumerRecords.last().timestamp()),
                    ZoneOffset.UTC,
                )
            }
            val behandlingsutfallRecrods: List<ConsumerRecord<String, String>> = consumerRecords.filter {
                val topicHeader = it.headers().headers("topic").first()
                topicHeader.value().toString(Charsets.UTF_8) == environment.oldBehandlingsutfallTopicHeader
            }
            if (behandlingsutfallRecrods.isNotEmpty()) {
                handleBehandlingsutfall(behandlingsutfallRecrods)
                totalRecords += behandlingsutfallRecrods.count()
            }
        }
    }

    private suspend fun handleBehandlingsutfall(consumerRecords: List<ConsumerRecord<String, String>>) = withContext(Dispatchers.IO) {
        val behandlingsutfalls = consumerRecords.map {
            val validationResult: ValidationResult = objectMapper.readValue(it.value())
            Behandlingsutfall(
                ruleHits = validationResult.ruleHits,
                sykmeldingId = it.key(),
                status = validationResult.status.name,
            )
        }
        behandlingsutfallDb.insertOrUpdateBatch(behandlingsutfalls)
    }
}
