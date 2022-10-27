package no.nav.sykmeldinger.status.db

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.sykmeldinger.application.db.DatabaseInterface
import org.postgresql.util.PGobject
import java.sql.Timestamp
import java.time.OffsetDateTime
import java.time.ZoneOffset

private val objectMapper: ObjectMapper = jacksonObjectMapper().apply {
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
}
fun toPGObject(jsonObject: Any) = PGobject().also {
    it.type = "json"
    it.value = objectMapper.writeValueAsString(jsonObject)
}

class SykmeldingStatusDB(
    private val database: DatabaseInterface
) {
    fun insertStatus(statusEvent: List<SykmeldingStatusKafkaEventDTO>): Int {
        database.connection.use { connection ->
            connection.prepareStatement(
                """
            insert into sykmeldingstatus(sykmelding_id, event, timestamp, arbeidsgiver, sporsmal) values(?, ?, ?, ?, ?) on conflict do nothing;
        """
            ).use { ps ->
                for (event in statusEvent) {
                    var index = 1
                    ps.setString(index++, event.sykmeldingId)
                    ps.setString(index++, event.statusEvent)
                    ps.setTimestamp(index++, Timestamp.from(event.timestamp.toInstant()))
                    ps.setObject(index++, event.arbeidsgiver?.let { toPGObject(it) })
                    ps.setObject(index, event.sporsmals?.let { toPGObject(it) })
                    ps.addBatch()
                }
                return ps.executeBatch().also {
                    connection.commit()
                }.size
            }
        }
    }

    fun updateStatusTimestamp(
        id: String,
        statusTime: OffsetDateTime,
        adjustedTimestamp: OffsetDateTime,
        statusEvent: String
    ) {
        database.connection.use { connection ->
            connection.prepareStatement(
                """
                UPDATE sykmeldingstatus SET timestamp = ? where sykmelding_id = ? and event = ? and timestamp = ?
                """
            ).use {
                it.setTimestamp(1, Timestamp.from(adjustedTimestamp.toInstant()))
                it.setString(2, id)
                it.setString(3, statusEvent)
                it.setTimestamp(4, Timestamp.from(statusTime.toInstant()))
                it.executeUpdate()
            }
            connection.commit()
        }
    }

    fun getFirstApenStatus(id: String): OffsetDateTime? {
        return database.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT * FROM sykmeldingstatus WHERE sykmelding_id = ? AND event = 'APEN' ORDER BY timestamp ASC LIMIT 1
                """
            ).use {
                it.setString(1, id)
                it.executeQuery().let { rs ->
                    if (rs.next()) {
                        return rs.getTimestamp("timestamp")?.toInstant()?.atOffset(ZoneOffset.UTC)
                    } else
                        null
                }
            }
        }
    }
}
