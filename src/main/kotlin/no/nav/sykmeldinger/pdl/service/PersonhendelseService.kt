package no.nav.sykmeldinger.pdl.service

import no.nav.sykmeldinger.application.metrics.NL_NAVN_COUNTER
import no.nav.sykmeldinger.identendring.IdentendringService
import no.nav.sykmeldinger.log
import no.nav.sykmeldinger.narmesteleder.db.NarmestelederDb
import no.nav.sykmeldinger.objectMapper
import no.nav.sykmeldinger.pdl.Endringstype
import no.nav.sykmeldinger.pdl.PersonhendelseDataClass
import no.nav.sykmeldinger.pdl.error.PersonNameNotFoundInPdl
import no.nav.sykmeldinger.pdl.error.PersonNotFoundInPdl
import no.nav.sykmeldinger.secureLog
import org.postgresql.util.PSQLException

class PersonhendelseService(
    private val identendringService: IdentendringService,
    private val narmestelederDb: NarmestelederDb,
    private val pdlPersonService: PdlPersonService,
) {
    suspend fun handlePersonhendelse(personhendelser: List<PersonhendelseDataClass>) {
        personhendelser
            .filter { it.opplysningstype == "FOLKEREGISTERIDENTIFIKATOR_V1" }
            .filter {
                it.endringstype == Endringstype.KORRIGERT ||
                    it.endringstype == Endringstype.OPPRETTET
            }
            .map { it.personidenter }
            .toSet()
            .forEach {
                try {
                    identendringService.updateIdent(it)
                } catch (ex: PSQLException) {
                    log.error("Error updating ident in database (see securelogs for more info)")
                    secureLog.error("Error updating identer", ex)
                }
                catch (ex: PersonNameNotFoundInPdl) {
                    logPersonhendelseError(
                        personhendelser,
                        it,
                        "Did not find name in PDL, continuing"
                    )
                } catch (ex: PersonNotFoundInPdl) {
                    logPersonhendelseError(personhendelser, it)
                    throw ex
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
