package no.nav.sykmeldinger.status.db

import no.nav.sykmeldinger.application.db.DatabaseInterface
import java.sql.Timestamp
import java.time.OffsetDateTime

fun DatabaseInterface.insertStatus(sykmeldingId: String, status: String, timestamp: OffsetDateTime) {
    connection.use { connection ->
        connection.prepareStatement(
            """
            insert into sykmeldingstatus(sykmelding_id, event, timestamp) values(?, ?, ?) on conflict do nothing;
        """
        ).use { ps ->
            var index = 1
            ps.setString(index++, sykmeldingId)
            ps.setString(index++, status)
            ps.setTimestamp(index, Timestamp.from(timestamp.toInstant()))
            ps.executeUpdate()
        }
        connection.commit()
    }
}
