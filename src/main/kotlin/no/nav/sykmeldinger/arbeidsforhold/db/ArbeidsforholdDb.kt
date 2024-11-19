package no.nav.sykmeldinger.arbeidsforhold.db

import io.opentelemetry.instrumentation.annotations.WithSpan
import java.sql.Date
import java.sql.ResultSet
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.sykmeldinger.application.db.DatabaseInterface
import no.nav.sykmeldinger.application.db.toList
import no.nav.sykmeldinger.arbeidsforhold.model.Arbeidsforhold
import no.nav.sykmeldinger.arbeidsforhold.model.ArbeidsforholdType

class ArbeidsforholdDb(
    private val database: DatabaseInterface,
) {
    suspend fun insertOrUpdate(arbeidsforhold: Arbeidsforhold) =
        withContext(Dispatchers.IO) {
            database.connection.use { connection ->
                connection
                    .prepareStatement(
                        """
               insert into arbeidsforhold(id, fnr, orgnummer, juridisk_orgnummer, orgnavn, fom, tom, type) 
               values (?, ?, ?, ?, ?, ?, ?, ?) on conflict (id) do update
                set fnr = excluded.fnr,
                    orgnummer = excluded.orgnummer,
                    juridisk_orgnummer = excluded.juridisk_orgnummer,
                    orgnavn = excluded.orgnavn,
                    fom = excluded.fom,
                    tom = excluded.tom,
                    type = excluded.type;
            """,
                    )
                    .use { preparedStatement ->
                        preparedStatement.setString(1, arbeidsforhold.id.toString())
                        preparedStatement.setString(2, arbeidsforhold.fnr)
                        preparedStatement.setString(3, arbeidsforhold.orgnummer)
                        preparedStatement.setString(4, arbeidsforhold.juridiskOrgnummer)
                        preparedStatement.setString(5, arbeidsforhold.orgNavn)
                        preparedStatement.setDate(6, Date.valueOf(arbeidsforhold.fom))
                        preparedStatement.setDate(
                            7,
                            arbeidsforhold.tom?.let { Date.valueOf(arbeidsforhold.tom) }
                        )
                        preparedStatement.setString(8, arbeidsforhold.type?.name)
                        preparedStatement.executeUpdate()
                    }
                connection.commit()
            }
        }

    suspend fun getArbeidsforhold(fnr: String): List<Arbeidsforhold> {
        return withContext(Dispatchers.IO) {
            database.connection.use {
                it.prepareStatement(
                        """
                    SELECT * FROM arbeidsforhold WHERE fnr = ?;
                """,
                    )
                    .use { ps ->
                        ps.setString(1, fnr)
                        ps.executeQuery().toList { toArbeidsforhold() }
                    }
            }
        }
    }

    fun deleteArbeidsforhold(id: Int) {
        database.connection.use { connection ->
            connection
                .prepareStatement(
                    """
                    DELETE FROM arbeidsforhold WHERE id = ?;
                """,
                )
                .use { ps ->
                    ps.setString(1, id.toString())
                    ps.executeUpdate()
                }
            connection.commit()
        }
    }

    suspend fun deleteArbeidsforholdIds(ids: List<Int>) {
        withContext(Dispatchers.IO) {
            database.connection.use { connection ->
                connection.prepareStatement("""DELETE FROM arbeidsforhold WHERE id = ?;""").use { ps
                    ->
                    for (id in ids) {
                        ps.setString(1, id.toString())
                        ps.addBatch()
                    }
                    ps.executeBatch()
                }
                connection.commit()
            }
        }
    }

    @WithSpan
    fun deleteOldArbeidsforhold(date: LocalDate): Int {
        database.connection.use { connection ->
            val result =
                connection
                    .prepareStatement(
                        """
                    DELETE FROM arbeidsforhold where tom is not null and tom < ?;
                """,
                    )
                    .use { ps ->
                        ps.setDate(1, Date.valueOf(date))
                        ps.executeUpdate()
                    }
            connection.commit()
            return result
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
        tom = getDate("tom")?.toLocalDate(),
        type = getString("type")?.let { ArbeidsforholdType.valueOf(it) }
    )
