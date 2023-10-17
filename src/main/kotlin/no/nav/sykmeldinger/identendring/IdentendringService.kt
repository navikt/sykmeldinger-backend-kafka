package no.nav.sykmeldinger.identendring

import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import no.nav.sykmeldinger.application.metrics.NYTT_FNR_COUNTER
import no.nav.sykmeldinger.log
import no.nav.sykmeldinger.pdl.service.PdlPersonService
import no.nav.sykmeldinger.secureLog
import no.nav.sykmeldinger.sykmelding.db.SykmeldingDb

suspend fun retry(times: Int = 3, delay: Duration = 1.seconds, block: suspend () -> Unit) {
    repeat(times) {
        try {
            block()
            return
        } catch (e: Exception) {
            log.warn("Error in retry function", e)
            if (it == times - 1) {
                throw e
            }
            delay(delay)
        }
    }
}

class IdentendringService(
    private val sykmeldingDb: SykmeldingDb,
    private val pdlService: PdlPersonService,
) {
    suspend fun updateIdent(ident: List<String>) {
        val person = pdlService.getPerson(ident.first(), UUID.randomUUID().toString())
        val oldFnrs = person.oldFnr

        updateFnr(person.fnr, oldFnrs)
    }

    private suspend fun updateFnr(nyttFnr: String, oldFnrs: List<String>) {

        if (oldFnrs.isEmpty()) {
            secureLog.info("Ingen endring av fnr for person: $nyttFnr")
            return
        }

        if (oldFnrs.size > 1) {
            secureLog.info(
                "Det finnes flere gamle fnr for person: $nyttFnr, med fnr: ${
                    oldFnrs.joinToString(
                        ",",
                    )
                }",
            )
        }

        oldFnrs.forEach { oldFnr ->
            secureLog.info("Prøver å oppdatere: $oldFnr til: $nyttFnr")
            retry {
                val updates = sykmeldingDb.updateFnr(oldFnr = oldFnr, newFNR = nyttFnr)

                val numberOfUpdates = updates.sumOf { it.updatedRows }
                if (numberOfUpdates > 0) {
                    secureLog.info("Oppdaterte fnr fra: $oldFnr til: $nyttFnr")
                    NYTT_FNR_COUNTER.inc()
                }
            }
        }
    }
}
