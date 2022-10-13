package no.nav.sykmeldinger.sykmelding.db

import no.nav.sykmeldinger.application.db.DatabaseInterface
import no.nav.sykmeldinger.objectMapper
import no.nav.sykmeldinger.sykmelding.model.Sykmelding
import no.nav.sykmeldinger.sykmelding.model.Sykmeldt
import org.postgresql.util.PGobject

class SykmeldingDb(
    private val database: DatabaseInterface
) {
    fun saveOrUpdate(sykmeldingId: String, sykmelding: Sykmelding, sykmeldt: Sykmeldt) {
        database.connection.use { connection ->
            connection.prepareStatement(
                """ 
                    insert into sykmelding(sykmelding_id, fnr, sykmelding) 
                    values (?, ?, ?) on conflict (sykmelding_id) do update
                     set fnr = excluded.fnr,
                         sykmelding = excluded.sykmelding;
                """.trimIndent()
            ).use { preparedStatement ->
                preparedStatement.setString(1, sykmeldingId)
                preparedStatement.setString(2, sykmeldt.fnr)
                preparedStatement.setObject(3, sykmelding.toPGObject())
                preparedStatement.executeUpdate()
            }
            connection.prepareStatement(
                """ 
                    insert into sykmeldt(fnr, fornavn, mellomnavn, etternavn) 
                    values (?, ?, ?, ?) on conflict (fnr) do update
                     set fornavn = excluded.fornavn,
                         mellomnavn = excluded.mellomnavn,
                         etternavn = excluded.etternavn;
                """.trimIndent()
            ).use { preparedStatement ->
                preparedStatement.setString(1, sykmeldt.fnr)
                preparedStatement.setString(2, sykmeldt.fornavn)
                preparedStatement.setString(3, sykmeldt.mellomnavn)
                preparedStatement.setString(4, sykmeldt.etternavn)
                preparedStatement.executeUpdate()
            }
            connection.commit()
        }
    }

    fun deleteSykmelding(sykmeldingId: String) {
        database.connection.use { connection ->
            connection.prepareStatement(
                """ 
                    delete from sykmelding where sykmelding_id = ?;
                """.trimIndent()
            ).use { preparedStatement ->
                preparedStatement.setString(1, sykmeldingId)
                preparedStatement.execute()
            }
            connection.prepareStatement(
                """ 
                    delete from behandlingsutfall where sykmelding_id = ?;
                """.trimIndent()
            ).use { preparedStatement ->
                preparedStatement.setString(1, sykmeldingId)
                preparedStatement.execute()
            }
            connection.prepareStatement(
                """ 
                    delete from sykmeldingstatus where sykmelding_id = ?;
                """.trimIndent()
            ).use { preparedStatement ->
                preparedStatement.setString(1, sykmeldingId)
                preparedStatement.execute()
            }
            connection.commit()
        }
    }
}

private fun Sykmelding.toPGObject() = PGobject().also {
    it.type = "json"
    it.value = objectMapper.writeValueAsString(this)
}
