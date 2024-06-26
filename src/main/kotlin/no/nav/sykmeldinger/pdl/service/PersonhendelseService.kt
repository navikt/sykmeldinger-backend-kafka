package no.nav.sykmeldinger.pdl.service

import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.sykmeldinger.application.metrics.NL_NAVN_COUNTER
import no.nav.sykmeldinger.identendring.IdentendringService
import no.nav.sykmeldinger.log
import no.nav.sykmeldinger.narmesteleder.db.NarmestelederDb
import no.nav.sykmeldinger.objectMapper
import no.nav.sykmeldinger.pdl.EndringstypeDataClass
import no.nav.sykmeldinger.pdl.PersonhendelseDataClass
import no.nav.sykmeldinger.pdl.error.PersonNameNotFoundInPdl
import no.nav.sykmeldinger.pdl.error.PersonNotFoundInPdl
import no.nav.sykmeldinger.secureLog
import org.postgresql.util.PSQLException

class PersonhendelseService(
    private val identendringService: IdentendringService,
    private val narmestelederDb: NarmestelederDb,
    private val pdlPersonService: PdlPersonService,
    private val cluster: String,
) {

    @WithSpan
    suspend fun handlePersonhendelse(personhendelser: List<PersonhendelseDataClass>) {
        personhendelser
            .filter { it.opplysningstype == "FOLKEREGISTERIDENTIFIKATOR_V1" }
            .filter {
                it.endringstype == EndringstypeDataClass.KORRIGERT ||
                    it.endringstype == EndringstypeDataClass.OPPRETTET
            }
            .map { it.personidenter }
            .toSet()
            .forEach {
                try {
                    identendringService.updateIdent(it)
                } catch (ex: PSQLException) {
                    log.error("Error updating ident in database (see securelogs for more info)")
                    secureLog.error("Error updating identer", ex)
                } catch (ex: PersonNameNotFoundInPdl) {
                    logPersonhendelseError(
                        personhendelser,
                        it,
                        "Did not find name in PDL, continuing"
                    )
                } catch (ex: PersonNotFoundInPdl) {
                    if (cluster == "prod-gcp") {
                        logPersonhendelseError(personhendelser, it)
                        throw ex
                    } else {
                        val hendelse =
                            personhendelser.first { hendelse ->
                                hendelse.personidenter.contains(it.first())
                            }
                        log.warn("Person not found in PDL, for hendelse $hendelse, skipping in dev")
                    }
                }
            }

        personhendelser
            .filter { it.navn != null }
            .forEach { personhendelse ->
                personhendelse.personidenter.forEach {
                    if (narmestelederDb.isNarmesteleder(it)) {
                        log.info(
                            "Oppdaterer navn med navn fra PDL for nærmeste leder for personhendelse ${personhendelse.hendelseId}"
                        )
                        val person = pdlPersonService.getPerson(it, personhendelse.hendelseId)
                        narmestelederDb.updateNavn(
                            it,
                            person.navn.toFormattedNameString(),
                        )
                        NL_NAVN_COUNTER.inc()
                    }
                }
            }
    }

    private fun logPersonhendelseError(
        personhendelser: List<PersonhendelseDataClass>,
        it: List<String>,
        logStatement: String = "Could not update ident for person in PDL"
    ) {
        val hendelse =
            personhendelser.first { hendelse -> hendelse.personidenter.contains(it.first()) }
        secureLog.error(
            "$logStatement ${objectMapper.writeValueAsString(hendelse)}",
        )
    }
}
