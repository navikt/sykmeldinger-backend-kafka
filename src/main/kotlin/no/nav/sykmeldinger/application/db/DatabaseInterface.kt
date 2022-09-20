package no.nav.sykmeldinger.application.db

import java.sql.Connection

interface DatabaseInterface {
    val connection: Connection
}
