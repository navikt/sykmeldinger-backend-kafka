package no.nav.sykmeldinger.arbeidsforhold.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.sykmeldinger.application.db.DatabaseInterface
import no.nav.sykmeldinger.application.db.toList
import no.nav.sykmeldinger.arbeidsforhold.model.Arbeidsforhold
import java.sql.Date
import java.sql.ResultSet

class ArbeidsforholdDb(
    private val database: DatabaseInterface
) {
    suspend fun insertOrUpdate(arbeidsforhold: Arbeidsforhold) = withContext(Dispatchers.IO) {
        database.connection.use { connection ->
            connection.prepareStatement(
                """
               insert into arbeidsforhold(id, fnr, orgnummer, juridisk_orgnummer, orgnavn, fom, tom) 
               values (?, ?, ?, ?, ?, ?, ?) on conflict (id) do update
                set fnr = excluded.fnr,
                    orgnummer = excluded.orgnummer,
                    juridisk_orgnummer = excluded.juridisk_orgnummer,
                    orgnavn = excluded.orgnavn,
                    fom = excluded.fom,
                    tom = excluded.tom;
            """
            ).use { preparedStatement ->
                preparedStatement.setString(1, arbeidsforhold.id.toString())
                preparedStatement.setString(2, arbeidsforhold.fnr)
                preparedStatement.setString(3, arbeidsforhold.orgnummer)
                preparedStatement.setString(4, arbeidsforhold.juridiskOrgnummer)
                preparedStatement.setString(5, arbeidsforhold.orgNavn)
                preparedStatement.setDate(6, Date.valueOf(arbeidsforhold.fom))
                preparedStatement.setDate(7, arbeidsforhold.tom?.let { Date.valueOf(arbeidsforhold.tom) })
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

    fun deleteArbeidsforhold(id: Int) {
        database.connection.use { connection ->
            connection.prepareStatement(
                """
                    DELETE FROM arbeidsforhold WHERE id = ?;
                """
            ).use { ps ->
                ps.setString(1, id.toString())
                ps.executeUpdate()
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
