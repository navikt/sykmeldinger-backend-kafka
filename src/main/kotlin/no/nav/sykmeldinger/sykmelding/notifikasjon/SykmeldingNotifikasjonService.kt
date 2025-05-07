package no.nav.sykmeldinger.sykmelding.notifikasjon

import java.time.LocalDateTime
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.Status
import no.nav.sykmeldinger.objectMapper
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

data class SykmeldingNotifikasjon(
    val sykmeldingId: String,
    val status: Status,
    val mottattDato: LocalDateTime,
    val fnr: String,
)

class SykmeldingNotifikasjonService(
    private val kafkaProducer: KafkaProducer<String, String>,
    private val sykmeldingnotifikasjonTopic: String
) {

    fun sendSykmeldingNotifikasjon(receivedSykmelding: ReceivedSykmelding) {
        val notifikasjon =
            SykmeldingNotifikasjon(
                sykmeldingId = receivedSykmelding.sykmelding.id,
                status = receivedSykmelding.validationResult.status,
                mottattDato = receivedSykmelding.mottattDato,
                fnr = receivedSykmelding.personNrPasient,
            )

        val producerRecord =
            ProducerRecord(
                sykmeldingnotifikasjonTopic,
                notifikasjon.sykmeldingId,
                objectMapper.writeValueAsString(notifikasjon)
            )
        kafkaProducer.send(producerRecord).get()
    }
}
