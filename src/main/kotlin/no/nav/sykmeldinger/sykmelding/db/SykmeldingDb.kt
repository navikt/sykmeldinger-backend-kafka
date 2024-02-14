package no.nav.sykmeldinger.sykmelding.db

import java.sql.Connection
import java.sql.Date
import java.sql.ResultSet
import java.sql.Types
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.model.RuleInfo
import no.nav.sykmeldinger.application.db.DatabaseInterface
import no.nav.sykmeldinger.application.db.toList
import no.nav.sykmeldinger.objectMapper
import no.nav.sykmeldinger.status.db.toPGObject
import no.nav.sykmeldinger.sykmelding.model.Sykmelding
import no.nav.sykmeldinger.sykmelding.model.Sykmeldt
import org.postgresql.util.PGobject
import org.postgresql.util.PSQLException

data class UpdateResult(val table: String, val updatedRows: Int)

class SykmeldingDb(
    private val database: DatabaseInterface,
) {

    suspend fun updateFnr(oldFnr: String, newFNR: String): List<UpdateResult> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<UpdateResult>()
            database.connection.use { connection ->
                results.add(
                    connection.updateFnrInTable("sykmelding", oldFnr = oldFnr, newFNR = newFNR)
                )
                if(connection.sykmeldtExists(newFNR)) {
                    results.add(connection.deleteSykmeldt(oldFnr))
                } else {
                    results.add(
                        connection.updateFnrInTable("sykmeldt", oldFnr = oldFnr, newFNR = newFNR)
                    )
                }
                results.add(
                    connection.updateFnrInTable("arbeidsforhold", oldFnr = oldFnr, newFNR = newFNR)
                )
                connection.commit()
            }
            results
        }

    private fun Connection.deleteSykmeldt(fnr: String) : UpdateResult {
        val update = prepareStatement("""
            delete from sykmeldt where fnr = ?;
        """.trimIndent()).use {
            it.setString(1, fnr)
            it.executeUpdate()
        }

        return UpdateResult("sykmeldt", update)
    }
     private fun Connection.sykmeldtExists(fnr: String) : Boolean {
        return prepareStatement("""
            select true from sykmeldt where fnr = ?;
        """).use {
            it.setString(1, fnr)
            it.executeQuery()?.next() ?: false
        }
    }


    private fun Connection.updateFnrInTable(
        table: String,
        oldFnr: String,
        newFNR: String
    ): UpdateResult {
        val updatedRows =
            prepareStatement(
                    """
               update $table set fnr = ? where fnr = ?;
            """,
                )
                .use { preparedStatement ->
                    preparedStatement.setString(1, newFNR)
                    preparedStatement.setString(2, oldFnr)
                    preparedStatement.executeUpdate()
                }
        return UpdateResult(table, updatedRows)
    }

    fun saveOrUpdate(
        sykmeldingId: String,
        sykmelding: Sykmelding,
        sykmeldt: Sykmeldt,
        okSykmelding: Boolean
    ) {
        database.connection.use { connection ->
            connection
                .prepareStatement(
                    """ 
                    insert into sykmelding(sykmelding_id, fnr, sykmelding) 
                    values (?, ?, ?) on conflict (sykmelding_id) do update
                     set fnr = excluded.fnr,
                         sykmelding = excluded.sykmelding;
                """
                        .trimIndent(),
                )
                .use { preparedStatement ->
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
            connection
                .prepareStatement(
                    """ 
                    delete from sykmelding where sykmelding_id = ?;
                """
                        .trimIndent(),
                )
                .use { preparedStatement ->
                    preparedStatement.setString(1, sykmeldingId)
                    preparedStatement.execute()
                }
            connection
                .prepareStatement(
                    """ 
                    delete from behandlingsutfall where sykmelding_id = ?;
                """
                        .trimIndent(),
                )
                .use { preparedStatement ->
                    preparedStatement.setString(1, sykmeldingId)
                    preparedStatement.execute()
                }
            connection
                .prepareStatement(
                    """ 
                    delete from sykmeldingstatus where sykmelding_id = ?;
                """
                        .trimIndent(),
                )
                .use { preparedStatement ->
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
                )
                .use { ps ->
                    ps.setString(1, fnr)
                    ps.executeQuery().toList { getString("sykmelding_id") }
                }
        }
    }

    fun getSykmeldt(fnr: String): Sykmeldt? {
        return database.connection.use { connection ->
            connection
                .prepareStatement(
                    """ 
                    select * from sykmeldt where fnr = ?;
                """
                        .trimIndent(),
                )
                .use { preparedStatement ->
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
                    insert into sykmeldt(fnr, fornavn, mellomnavn, etternavn, foedselsdato) 
                    values (?, ?, ?, ?, ?) on conflict (fnr) do update
                     set fornavn = excluded.fornavn,
                         mellomnavn = excluded.mellomnavn,
                         etternavn = excluded.etternavn,
                         foedselsdato = excluded.foedselsdato;
            """
                    .trimIndent(),
            )
            .use { ps ->
                ps.setString(1, sykmeldt.fnr)
                ps.setString(2, sykmeldt.fornavn)
                ps.setString(3, sykmeldt.mellomnavn)
                ps.setString(4, sykmeldt.etternavn)
                if (sykmeldt.foedselsdato != null) {
                    ps.setDate(5, Date.valueOf(sykmeldt.foedselsdato))
                } else {
                    ps.setNull(5, Types.DATE)
                }
                ps.executeUpdate()
            }
    }

    private fun Connection.insertOKBehandlingsutfall(sykmeldingId: String) {
        prepareStatement(
                """
               insert into behandlingsutfall(sykmelding_id, behandlingsutfall, rule_hits) values(?, ?, ?) on conflict(sykmelding_id) do nothing;
            """,
            )
            .use { ps ->
                var index = 1
                ps.setString(index++, sykmeldingId)
                ps.setString(index++, "OK")
                ps.setObject(index, toPGObject(emptyList<RuleInfo>()))
                ps.executeUpdate()
            }
    }
}

fun ResultSet.toSykmeldt(): Sykmeldt {
    val foedselsdato =
        if (getDate("foedselsdato") != null) LocalDate.parse(getString("foedselsdato")) else null
    return Sykmeldt(
        fnr = getString("fnr"),
        fornavn = getString("fornavn"),
        mellomnavn = getString("mellomnavn"),
        etternavn = getString("etternavn"),
        foedselsdato = foedselsdato,
    )
}

private fun Sykmelding.toPGObject() =
    PGobject().also {
        it.type = "json"
        it.value = objectMapper.writeValueAsString(this)
    }
