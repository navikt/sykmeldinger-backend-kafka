package no.nav.sykmeldinger.narmesteleder.db

import no.nav.sykmeldinger.application.db.DatabaseInterface
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.ZoneOffset

class NarmestelederDb(
    private val database: DatabaseInterface
) {
    fun insertOrUpdate(narmesteleder: NarmestelederDbModel) {
        database.connection.use { connection ->
            connection.prepareStatement(
                """
               insert into narmesteleder(narmeste_leder_id, orgnummer, bruker_fnr, narmeste_leder_fnr, navn, timestamp) 
               values (?, ?, ?, ?, ?, ?) on conflict (narmeste_leder_id) do update
                set orgnummer = ?,
                    bruker_fnr = ?,
                    narmeste_leder_fnr = ?,
                    navn = ?,
                    timestamp = ?;
            """
            ).use { preparedStatement ->
                preparedStatement.setString(1, narmesteleder.narmestelederId)
                // insert
                preparedStatement.setString(2, narmesteleder.orgnummer)
                preparedStatement.setString(3, narmesteleder.brukerFnr)
                preparedStatement.setString(4, narmesteleder.lederFnr)
                preparedStatement.setString(5, narmesteleder.navn)
                preparedStatement.setTimestamp(6, Timestamp.from(narmesteleder.timestamp.toInstant()))
                // update
                preparedStatement.setString(7, narmesteleder.orgnummer)
                preparedStatement.setString(8, narmesteleder.brukerFnr)
                preparedStatement.setString(9, narmesteleder.lederFnr)
                preparedStatement.setString(10, narmesteleder.navn)
                preparedStatement.setTimestamp(11, Timestamp.from(narmesteleder.timestamp.toInstant()))
                preparedStatement.executeUpdate()
            }
            connection.commit()
        }
    }

    fun remove(narmestelederId: String) {
        database.connection.use { connection ->
            connection.prepareStatement(
                """
               delete from narmesteleder where narmeste_leder_id = ?;
            """
            ).use { ps ->
                ps.setString(1, narmestelederId)
                ps.executeUpdate()
            }
            connection.commit()
        }
    }
}

fun ResultSet.toNarmestelederDbModel(): NarmestelederDbModel =
    NarmestelederDbModel(
        narmestelederId = getString("narmeste_leder_id"),
        orgnummer = getString("orgnummer"),
        brukerFnr = getString("bruker_fnr"),
        lederFnr = getString("narmeste_leder_fnr"),
        navn = getString("navn"),
        timestamp = getTimestamp("timestamp").toInstant().atOffset(ZoneOffset.UTC)
    )
