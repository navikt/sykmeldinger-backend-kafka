package no.nav.sykmeldinger.sykmelding.db

import no.nav.syfo.model.RuleInfo
import no.nav.sykmeldinger.application.db.DatabaseInterface
import no.nav.sykmeldinger.application.db.toList
import no.nav.sykmeldinger.objectMapper
import no.nav.sykmeldinger.status.db.toPGObject
import no.nav.sykmeldinger.sykmelding.model.Sykmelding
import no.nav.sykmeldinger.sykmelding.model.Sykmeldt
import org.postgresql.util.PGobject
import java.sql.Connection
import java.sql.ResultSet

class SykmeldingDb(
    private val database: DatabaseInterface,
) {
    fun saveOrUpdate(sykmeldingId: String, sykmelding: Sykmelding, sykmeldt: Sykmeldt, okSykmelding: Boolean) {
        database.connection.use { connection ->
            connection.prepareStatement(
                """ 
                    insert into sykmelding(sykmelding_id, fnr, sykmelding) 
                    values (?, ?, ?) on conflict (sykmelding_id) do update
                     set fnr = excluded.fnr,
                         sykmelding = excluded.sykmelding;
                """.trimIndent(),
            ).use { preparedStatement ->
                preparedStatement.setString(1, sykmeldingId)
                preparedStatement.setString(2, sykmeldt.fnr)
                preparedStatement.setObject(3, sykmelding.toPGObject())
                preparedStatement.executeUpdate()
            }
            connection.saveOrUpdateSykmeldt(sykmeldt)
            if (okSykmelding) {
                connection.insertOKBehandlingsutfall(sykmeldingId)
            }
            connection.commit()
        }
    }

    fun deleteSykmelding(sykmeldingId: String) {
        database.connection.use { connection ->
            connection.prepareStatement(
                """ 
                    delete from sykmelding where sykmelding_id = ?;
                """.trimIndent(),
            ).use { preparedStatement ->
                preparedStatement.setString(1, sykmeldingId)
                preparedStatement.execute()
            }
            connection.prepareStatement(
                """ 
                    delete from behandlingsutfall where sykmelding_id = ?;
                """.trimIndent(),
            ).use { preparedStatement ->
                preparedStatement.setString(1, sykmeldingId)
                preparedStatement.execute()
            }
            connection.prepareStatement(
                """ 
                    delete from sykmeldingstatus where sykmelding_id = ?;
                """.trimIndent(),
            ).use { preparedStatement ->
                preparedStatement.setString(1, sykmeldingId)
                preparedStatement.execute()
            }
            connection.commit()
        }
    }

    fun getSykmeldingIds(fnr: String): List<String> {
        return database.connection.use {
            it.prepareStatement(
                """
                    SELECT sykmelding_id FROM sykmelding WHERE fnr = ?;
                """,
            ).use { ps ->
                ps.setString(1, fnr)
                ps.executeQuery().toList { getString("sykmelding_id") }
            }
        }
    }

    fun updateFnr(nyttFnr: String, sykmeldingId: String) {
        database.connection.use { connection ->
            connection.prepareStatement(
                """
               update sykmelding set fnr = ? where sykmelding_id = ?;
            """,
            ).use { preparedStatement ->
                preparedStatement.setString(1, nyttFnr)
                preparedStatement.setString(2, sykmeldingId)
                preparedStatement.executeUpdate()
            }
            connection.commit()
        }
    }

    fun getSykmeldt(fnr: String): Sykmeldt? {
        return database.connection.use { connection ->
            connection.prepareStatement(
                """ 
                    select * from sykmeldt where fnr = ?;
                """.trimIndent(),
            ).use { preparedStatement ->
                preparedStatement.setString(1, fnr)
                preparedStatement.executeQuery().toList { toSykmeldt() }.firstOrNull()
            }
        }
    }

    fun saveOrUpdateSykmeldt(sykmeldt: Sykmeldt) {
        database.connection.use { connection ->
            connection.saveOrUpdateSykmeldt(sykmeldt)
            connection.commit()
        }
    }

    private fun Connection.saveOrUpdateSykmeldt(sykmeldt: Sykmeldt) {
        prepareStatement(
            """ 
                    insert into sykmeldt(fnr, fornavn, mellomnavn, etternavn) 
                    values (?, ?, ?, ?) on conflict (fnr) do update
                     set fornavn = excluded.fornavn,
                         mellomnavn = excluded.mellomnavn,
                         etternavn = excluded.etternavn;
            """.trimIndent(),
        ).use { ps ->
            ps.setString(1, sykmeldt.fnr)
            ps.setString(2, sykmeldt.fornavn)
            ps.setString(3, sykmeldt.mellomnavn)
            ps.setString(4, sykmeldt.etternavn)
            ps.executeUpdate()
        }
    }

    fun Connection.insertOKBehandlingsutfall(sykmeldingId: String) {
        prepareStatement(
            """
               insert into behandlingsutfall(sykmelding_id, behandlingsutfall, rule_hits) values(?, ?, ?) on conflict(sykmelding_id) do nothing;
            """,
        ).use { ps ->
            var index = 1
            ps.setString(index++, sykmeldingId)
            ps.setString(index++, "OK")
            ps.setObject(index, toPGObject(emptyList<RuleInfo>()))
            ps.executeUpdate()
        }
    }

    fun deleteSykmeldt(fnr: String) {
        database.connection.use { connection ->
            connection.prepareStatement(
                """ 
                    delete from sykmeldt where fnr = ?;
                """.trimIndent(),
            ).use { preparedStatement ->
                preparedStatement.setString(1, fnr)
                preparedStatement.execute()
            }
            connection.commit()
        }
    }
}

fun ResultSet.toSykmeldt(): Sykmeldt =
    Sykmeldt(
        fnr = getString("fnr"),
        fornavn = getString("fornavn"),
        mellomnavn = getString("mellomnavn"),
        etternavn = getString("etternavn"),
    )

private fun Sykmelding.toPGObject() = PGobject().also {
    it.type = "json"
    it.value = objectMapper.writeValueAsString(this)
}
