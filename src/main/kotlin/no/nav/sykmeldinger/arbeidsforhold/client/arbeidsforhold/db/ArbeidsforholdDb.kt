package no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.db

import no.nav.sykmeldinger.application.db.DatabaseInterface
import no.nav.sykmeldinger.application.db.toList
import no.nav.sykmeldinger.arbeidsforhold.model.Arbeidsforhold
import no.nav.sykmeldinger.narmesteleder.db.NarmestelederDbModel
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.ZoneOffset

class ArbeidsforholdDb(
    private val database: DatabaseInterface
) {
    fun insertOrUpdate(arbeidsforhold: Arbeidsforhold) {
        database.connection.use { connection ->
            connection.prepareStatement(
                """
               insert into arbeidsforhold(id, fnr, orgnummer, juridisk_orgnummer, orgnavn, fom, tom) 
               values (?, ?, ?, ?, ?, ?, ?) on conflict (id) do update
                set fnr = ?,
                    orgnummer = ?,
                    juridisk_orgnummer = ?,
                    orgnavn = ?,
                    fom = ?,
                    tom = ?;
            """
            ).use { preparedStatement ->
                preparedStatement.setString(1, arbeidsforhold.id.toString())
                // insert
                preparedStatement.setString(2, arbeidsforhold.fnr)
                preparedStatement.setString(3, arbeidsforhold.orgnummer)
                preparedStatement.setString(4, arbeidsforhold.juridiskOrgnummer)
                preparedStatement.setString(5, arbeidsforhold.orgNavn)
                preparedStatement.setDate(6, arbeidsforhold.fom)
                preparedStatement.setDate(7, arbeidsforhold.tom)
                // update
                preparedStatement.setString(8, arbeidsforhold.fnr)
                preparedStatement.setString(9, arbeidsforhold.orgnummer)
                preparedStatement.setString(10, arbeidsforhold.juridiskOrgnummer)
                preparedStatement.setString(11, arbeidsforhold.orgNavn)
                preparedStatement.setDate(12, arbeidsforhold.fom)
                preparedStatement.setDate(13, arbeidsforhold.tom)
                preparedStatement.executeUpdate()
            }
            connection.commit()
        }
    }

    fun getArbeidsforhold(fnr: String): List<Arbeidsforhold> {
        return database.connection.use {
            it.prepareStatement(
                """
                    SELECT * FROM arbeidsforhold WHERE fnr = ?;
                """
            ).use { ps ->
                ps.setString(1, fnr)
                ps.executeQuery().toList { toArbeidsforhold() }
            }
        }
    }

    fun updateFnr(nyttFnr: String, id: Int) {
        database.connection.use { connection ->
            connection.prepareStatement(
                """
               update arbeidsforhold set fnr = ? where id = ?;
            """
            ).use { preparedStatement ->
                preparedStatement.setString(1, nyttFnr)
                preparedStatement.setString(2, id.toString())
                preparedStatement.executeUpdate()
            }
            connection.commit()
        }
    }
}

fun ResultSet.toArbeidsforhold(): Arbeidsforhold =
    Arbeidsforhold(
        id = getString("id").toInt(),
        fnr = getString("fnr"),
        orgnummer = getString("orgnummer"),
        juridiskOrgnummer = getString("juridisk_orgnummer"),
        orgNavn = getString("orgnavn"),
        fom = getDate("fom").toLocalDate(),
        tom = getDate("tom")?.toLocalDate()
    )
