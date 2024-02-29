package no.nav.sykmeldinger.behandlingsutfall.db

import no.nav.sykmeldinger.application.db.DatabaseInterface
import no.nav.sykmeldinger.behandlingsutfall.Behandlingsutfall
import no.nav.sykmeldinger.status.db.toPGObject

class BehandlingsutfallDB(private val database: DatabaseInterface) {
    fun insertOrUpdateBatch(behandlingsutfall: List<Behandlingsutfall>): Int {
        return database.connection.use { connection ->
            connection
                .prepareStatement(
                    """
               insert into behandlingsutfall(sykmelding_id, behandlingsutfall, rule_hits) values(?, ?, ?) on conflict(sykmelding_id) do nothing ;
            """,
                )
                .use { ps ->
                    for (utfall in behandlingsutfall) {
                        var index = 1
                        ps.setString(index++, utfall.sykmeldingId)
                        ps.setString(index++, utfall.status)
                        ps.setObject(index, toPGObject(utfall.ruleHits))
                        ps.addBatch()
                    }
                    ps.executeBatch().also { connection.commit() }.size
                }
        }
    }
}
