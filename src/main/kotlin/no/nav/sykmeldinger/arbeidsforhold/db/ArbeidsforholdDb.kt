package no.nav.sykmeldinger.arbeidsforhold.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.sykmeldinger.application.db.DatabaseInterface
import no.nav.sykmeldinger.arbeidsforhold.model.Arbeidsforhold
import java.sql.Date

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
}
