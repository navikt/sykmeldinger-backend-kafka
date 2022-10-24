package no.nav.sykmeldinger.arbeidsforhold.delete

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.sykmeldinger.application.ApplicationState
import no.nav.sykmeldinger.application.leaderelection.LeaderElection
import no.nav.sykmeldinger.application.metrics.SLETTET_ARBFORHOLD_COUNTER
import no.nav.sykmeldinger.arbeidsforhold.db.ArbeidsforholdDb
import no.nav.sykmeldinger.log
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetTime
import java.time.ZoneOffset

class DeleteArbeidsforholdService(
    private val arbeidsforholdDb: ArbeidsforholdDb,
    private val leaderElection: LeaderElection,
    private val applicationState: ApplicationState
) {
    companion object {
        private const val MONTHS_FOR_ARBEIDSFORHOLD = 4L
    }

    @DelicateCoroutinesApi
    fun start() {
        GlobalScope.launch(Dispatchers.IO) {
            while (applicationState.ready) {
                if (leaderElection.isLeader()) {
                    try {
                        val result = arbeidsforholdDb.deleteOldArbeidsforhold(getDateForDeletion())
                        log.info("Deleted $result arbeidsforhold")
                        SLETTET_ARBFORHOLD_COUNTER.inc(result.toDouble())
                    } catch (ex: Exception) {
                        log.error("Could not delete arbeidsforhold", ex)
                    }
                }
                delay(
                    getDelayTime(
                        start = OffsetTime.of(LocalTime.of(7, 0), ZoneOffset.UTC),
                        now = OffsetTime.now(ZoneOffset.UTC)
                    )
                )
            }
        }
    }

    private fun getDateForDeletion() = LocalDate.now().minusMonths(MONTHS_FOR_ARBEIDSFORHOLD)
}

fun getDelayTime(start: OffsetTime, now: OffsetTime): Long {
    val duration = Duration.between(now, start).toMillis()
    val etDogn = 86_400_000L
    return if (duration >= 0) {
        duration
    } else {
        etDogn + duration
    }
}
