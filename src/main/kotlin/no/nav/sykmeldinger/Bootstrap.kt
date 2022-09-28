package no.nav.sykmeldinger

import io.prometheus.client.hotspot.DefaultExports
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import no.nav.sykmeldinger.application.ApplicationServer
import no.nav.sykmeldinger.application.ApplicationState
import no.nav.sykmeldinger.application.createApplicationEngine
import no.nav.sykmeldinger.application.db.Database
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
    val kafkaConsumer = getSykmeldingStatusKafkaConsumer()
    val sykmeldingStatusConsumer = SykmeldingStatusConsumer(env, kafkaConsumer, database, applicationState)
    sykmeldingStatusConsumer.startConsumer()
    applicationServer.start()
}

private fun getSykmeldingStatusKafkaConsumer(): KafkaConsumer<String, SykmeldingStatusKafkaMessageDTO> {
    val kafkaConsumer = KafkaConsumer(
        KafkaUtils.getAivenKafkaConfig().also {
            it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
            it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = 1000
        }.toConsumerConfig("sykmeldinger-kafka-bekreftet", JacksonKafkaDeserializer::class),
        StringDeserializer(),
        JacksonKafkaDeserializer(SykmeldingStatusKafkaMessageDTO::class)
    )
    return kafkaConsumer
}
