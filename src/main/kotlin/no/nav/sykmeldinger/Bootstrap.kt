package no.nav.sykmeldinger

import io.prometheus.client.hotspot.DefaultExports
import no.nav.sykmeldinger.application.ApplicationServer
import no.nav.sykmeldinger.application.ApplicationState
import no.nav.sykmeldinger.application.createApplicationEngine
import no.nav.sykmeldinger.application.db.Database
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val log: Logger = LoggerFactory.getLogger("no.nav.sykmeldinger.sykmeldinger-backend-kafka")

fun main() {
    val env = Environment()
    DefaultExports.initialize()
    val applicationState = ApplicationState()
    val applicationEngine = createApplicationEngine(
        env,
        applicationState
    )
    val applicationServer = ApplicationServer(applicationEngine, applicationState)
    val database = Database(env)
    applicationServer.start()
}
