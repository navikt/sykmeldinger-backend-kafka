package no.nav.sykmeldinger.arbeidsforhold

import java.time.LocalDate
import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.client.ArbeidsforholdClient
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.model.Ansettelsesperiode
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.model.ArbeidsstedType
import no.nav.sykmeldinger.arbeidsforhold.client.organisasjon.client.OrganisasjonsinfoClient
import no.nav.sykmeldinger.arbeidsforhold.db.ArbeidsforholdDb
import no.nav.sykmeldinger.arbeidsforhold.model.Arbeidsforhold
import no.nav.sykmeldinger.secureLog

class ArbeidsforholdService(
    private val arbeidsforholdClient: ArbeidsforholdClient,
    private val organisasjonsinfoClient: OrganisasjonsinfoClient,
    private val arbeidsforholdDb: ArbeidsforholdDb,
) {
    suspend fun insertOrUpdate(arbeidsforhold: Arbeidsforhold) {
        arbeidsforholdDb.insertOrUpdate(arbeidsforhold)
    }

    suspend fun getArbeidsforhold(
        fnr: String,
        sykmeldingFom: LocalDate,
        sykmeldingTom: LocalDate
    ): List<Arbeidsforhold> {
        val arbeidsgivere = arbeidsforholdClient.getArbeidsforhold(fnr = fnr)

        if (arbeidsgivere.isEmpty()) {
            return emptyList()
        }
        val ansettelsesperiodePairs =
            arbeidsgivere.map { it.ansettelsesperiode.startdato to it.ansettelsesperiode.sluttdato }
        secureLogInfo(
            ansettelsesPerioder = ansettelsesperiodePairs.toTypedArray(),
            sykmeldingsPeriode = sykmeldingFom to sykmeldingTom,
            fnr = fnr,
            tekst = "Getting all arbeidsforhold"
        )
        val arbeidsgiverList =
            arbeidsgivere
                .filter {
                    it.arbeidssted.type == ArbeidsstedType.Underenhet &&
                        erArbeidsforholdGyldig(it.ansettelsesperiode, sykmeldingFom, sykmeldingTom)
                }
                .sortedWith(
                    compareByDescending(nullsLast()) { it.ansettelsesperiode.sluttdato },
                )
                .map { aaregArbeidsforhold ->
                    val organisasjonsinfo =
                        organisasjonsinfoClient.getOrganisasjonsnavn(
                            aaregArbeidsforhold.arbeidssted.getOrgnummer()
                        )
                    Arbeidsforhold(
                        id = aaregArbeidsforhold.navArbeidsforholdId,
                        fnr = fnr,
                        orgnummer = aaregArbeidsforhold.arbeidssted.getOrgnummer(),
                        juridiskOrgnummer =
                            aaregArbeidsforhold.opplysningspliktig.getJuridiskOrgnummer(),
                        orgNavn = organisasjonsinfo.navn.getNameAsString(),
                        fom = aaregArbeidsforhold.ansettelsesperiode.startdato,
                        tom = aaregArbeidsforhold.ansettelsesperiode.sluttdato,
                    )
                }
        val endeligAnsettelsesperiodePairs = arbeidsgiverList.map { it.fom to it.tom }
        secureLogInfo(
            ansettelsesPerioder = endeligAnsettelsesperiodePairs.toTypedArray(),
            sykmeldingsPeriode = sykmeldingFom to sykmeldingTom,
            fnr = fnr,
            tekst = "Filtered out valid arbeidsforhold"
        )
        return arbeidsgiverList
    }

    private fun secureLogInfo(
        vararg ansettelsesPerioder: Pair<LocalDate, LocalDate?>,
        sykmeldingsPeriode: Pair<LocalDate, LocalDate>,
        fnr: String,
        tekst: String,
    ) {
        val ansettelsesPerioder =
            ansettelsesPerioder.map { periode -> "${periode.first} - ${periode.second}" }
        val sykmeldingFomTom = "${sykmeldingsPeriode.first} - ${sykmeldingsPeriode.second}"
        secureLog.info(
            "$tekst {} {} {}",
            kv("fnr", fnr),
            kv("sykmeldingsperiode", sykmeldingFomTom),
            kv("ansettelsesperioder", ansettelsesPerioder)
        )
    }

    suspend fun getArbeidsforholdFromDb(fnr: String): List<Arbeidsforhold> {
        return arbeidsforholdDb.getArbeidsforhold(fnr)
    }

    fun deleteArbeidsforhold(id: Int) {
        arbeidsforholdDb.deleteArbeidsforhold(id)
    }

    private fun erArbeidsforholdGyldig(
        ansettelsesperiode: Ansettelsesperiode,
        sykmeldingFom: LocalDate,
        sykmeldingTom: LocalDate,
    ): Boolean {
        val checkSluttdato =
            ansettelsesperiode.sluttdato == null ||
                ansettelsesperiode.sluttdato.isAfter(sykmeldingFom) ||
                ansettelsesperiode.sluttdato == sykmeldingFom
        val checkStartdato =
            ansettelsesperiode.startdato.isBefore(sykmeldingTom) ||
                ansettelsesperiode.startdato == sykmeldingTom
        return checkStartdato && checkSluttdato
    }

    suspend fun deleteArbeidsforholdIds(deleted: List<Int>) {
        arbeidsforholdDb.deleteArbeidsforholdIds(deleted)
    }
}
